/*
   Copyright 2019 Evan Saulpaugh

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.esaulpaugh.headlong.abi;

import com.esaulpaugh.headlong.util.SuperSerial;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import static com.esaulpaugh.headlong.abi.Encoding.OFFSET_LENGTH_BYTES;
import static com.esaulpaugh.headlong.abi.Encoding.UINT31;
import static com.esaulpaugh.headlong.abi.UnitType.UNIT_LENGTH_BYTES;

/** @see ABIType */
public final class TupleType extends ABIType<Tuple> implements Iterable<ABIType<?>> {

    private static final String EMPTY_TUPLE_STRING = "()";

    public static final TupleType EMPTY = new TupleType(EMPTY_TUPLE_STRING, false, EMPTY_ARRAY);

    final ABIType<?>[] elementTypes;
    private final int staticByteLen;

    private TupleType(String canonicalType, boolean dynamic, ABIType<?>[] elementTypes) {
        super(canonicalType, Tuple.class, dynamic);
        this.elementTypes = elementTypes;
        this.staticByteLen = dynamic ? OFFSET_LENGTH_BYTES : staticTupleLen(this);
    }

    static TupleType wrap(ABIType<?>... elements) {
        StringBuilder canonicalBuilder = new StringBuilder("(");
        boolean dynamic = false;
        for (ABIType<?> e : elements) {
            canonicalBuilder.append(e.canonicalType).append(',');
            dynamic |= e.dynamic;
        }
        return new TupleType(completeTupleTypeString(canonicalBuilder), dynamic, elements); // TODO .intern() string?
    }

    public int size() {
        return elementTypes.length;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public ABIType<?> get(int index) {
        return elementTypes[index];
    }

    @Override
    Class<?> arrayClass() {
        return Tuple[].class;
    }

    @Override
    public int typeCode() {
        return TYPE_CODE_TUPLE;
    }

    @Override
    int staticByteLength() {
        return staticByteLen;
    }

    @Override
    int dynamicByteLength(Object value) {
        final Object[] elements = ((Tuple) value).elements;
        return countBytes(i -> measureObject(get(i), elements[i]));
    }

    @Override
    int byteLength(Object value) {
        if(!dynamic) return staticByteLen;
        final Object[] elements = ((Tuple) value).elements;
        return countBytes(i -> measureObject(get(i), elements[i]));
    }

    private static int measureObject(ABIType<?> type, Object value) {
        return totalLen(type.byteLength(value), type.dynamic);
    }

    /**
     * @param value the Tuple being measured. {@code null} if not available
     * @return the length in bytes of the non-standard packed encoding
     */
    @Override
    public int byteLengthPacked(Object value) {
        final Object[] elements = value != null ? ((Tuple) value).elements : new Object[size()];
        return countBytes(i -> get(i).byteLengthPacked(elements[i]));
    }

    private int countBytes(IntUnaryOperator counter) {
        return countBytes(false, elementTypes.length, 0, counter);
    }

    static int countBytes(boolean array, int len, int count, IntUnaryOperator counter) {
        int i = 0;
        try {
            for ( ; i < len; i++) {
                count += counter.applyAsInt(i);
            }
            return count;
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException((array ? "array" : "tuple") + " index " + i + ": " + iae.getMessage(), iae);
        }
    }

    @Override
    public int validate(final Tuple value) {
        if (value.size() == this.size()) {
            return countBytes(i -> validateObject(get(i), value.elements[i]));
        }
        throw new IllegalArgumentException("tuple length mismatch: actual != expected: " + value.size() + " != " + this.size());
    }

    private static int validateObject(ABIType<?> type, Object value) {
        try {
            return totalLen(type._validate(value), type.dynamic);
        } catch (NullPointerException npe) {
            throw new IllegalArgumentException("null", npe);
        }
    }

    static int totalLen(int byteLen, boolean addUnit) {
        return addUnit ? UNIT_LENGTH_BYTES + byteLen : byteLen;
    }

    @Override
    void encodeTail(Object value, ByteBuffer dest) {
        Object[] vals = ((Tuple) value).elements;
        encodeObjects(dynamic, vals, TupleType.this::get, dest, dynamic ? headLength(vals) : -1);
    }

    @Override
    void encodePackedUnchecked(Tuple value, ByteBuffer dest) {
        final int size = size();
        for (int i = 0; i < size; i++) {
            get(i).encodeObjectPackedUnchecked(value.elements[i], dest);
        }
    }

    static void encodeObjects(boolean dynamic, Object[] values, IntFunction<ABIType<?>> getType, ByteBuffer dest, int offset) {
        for (int i = 0; i < values.length; i++) {
            offset = getType.apply(i).encodeHead(values[i], dest, offset);
        }
        if(dynamic) {
            for (int i = 0; i < values.length; i++) {
                ABIType<?> t = getType.apply(i);
                if (t.dynamic) {
                    t.encodeTail(values[i], dest);
                }
            }
        }
    }

    private int headLength(Object[] elements) {
        int sum = 0;
        for (int i = 0; i < elements.length; i++) {
            sum += get(i).staticByteLength();
        }
        return sum;
    }

    @Override
    Tuple decode(ByteBuffer bb, byte[] unitBuffer) {
        Object[] elements = new Object[size()];
        decodeObjects(bb, unitBuffer, TupleType.this::get, elements);
        return new Tuple(elements);
    }

    @SuppressWarnings("unchecked")
    public <T> T decode(ByteBuffer bb, int... indices) {
        bb.mark();
        try {
            if(indices.length == 1) {
                return decodeIndex(bb, indices[0]); // decodes and returns specified element
            }
            if(indices.length == 0) {
                throw new IllegalArgumentException("must specify at least one index");
            }
            return (T) decodeIndices(bb, indices); // decodes and returns specified elements
        } finally {
            bb.reset();
        }
    }

    private void ensureIndexInBounds(int index) {
        if (index < 0 || index >= elementTypes.length) {
            throw new IllegalArgumentException("bad index: " + index);
        }
    }

    private <T> T decodeIndex(ByteBuffer bb, int index) {
        ensureIndexInBounds(index);
        int skipBytes = 0;
        for (int j = 0; j < index; j++) {
            skipBytes += calcSkipBytes(elementTypes[j]);
        }
        final int pos = bb.position();
        bb.position(pos + skipBytes);
        @SuppressWarnings("unchecked")
        final ABIType<T> resultType = (ABIType<T>) elementTypes[index];
        final byte[] unitBuffer = newUnitBuffer();
        if (resultType.dynamic) {
            bb.position(pos + UINT31.decode(bb, unitBuffer));
        }
        return resultType.decode(bb, unitBuffer);
    }

    private Tuple decodeIndices(ByteBuffer bb, int... indices) {
        final Object[] results = new Object[elementTypes.length];
        final int pos = bb.position();
        final byte[] unitBuffer = newUnitBuffer();
        int i = 0, j = 0, index, skipBytes = 0;
        int prevIdx = -1;
        do {
            index = indices[i++];
            ensureIndexInBounds(index);
            if(index <= prevIdx) throw new IllegalArgumentException("index out of order: " + index);
            for (; j < index; j++) {
                skipBytes += calcSkipBytes(elementTypes[j]);
                results[j] = Tuple.ABSENT;
            }
            final ABIType<?> result = elementTypes[j++];
            final int startElement = pos + skipBytes;
            bb.position(startElement);
            if (result.dynamic) {
                bb.position(pos + UINT31.decode(bb, unitBuffer));
                results[index] = result.decode(bb, unitBuffer);
                skipBytes = startElement + OFFSET_LENGTH_BYTES;
            } else {
                results[index] = result.decode(bb, unitBuffer);
                skipBytes = bb.position() - pos;
            }
            prevIdx = index;
        } while (i < indices.length);
        for (; j < results.length; j++) {
            results[j] = Tuple.ABSENT;
        }
        return new Tuple(results);
    }

    private int calcSkipBytes(ABIType<?> skipped) {
        switch (skipped.typeCode()) {
        case TYPE_CODE_ARRAY: return skipped.dynamic ? OFFSET_LENGTH_BYTES : ArrayType.staticArrLen(skipped);
        case TYPE_CODE_TUPLE: return skipped.dynamic ? OFFSET_LENGTH_BYTES : staticTupleLen(skipped);
        default: return UNIT_LENGTH_BYTES;
        }
    }

    static int staticTupleLen(ABIType<?> tt) {
        int len = 0;
        for (ABIType<?> e : (TupleType) tt) {
            switch (e.typeCode()) {
            case TYPE_CODE_ARRAY: len += ArrayType.staticArrLen(e); continue;
            case TYPE_CODE_TUPLE: len += staticTupleLen(e); continue;
            default: len += UNIT_LENGTH_BYTES;
            }
        }
        return len;
    }

    static void decodeObjects(ByteBuffer bb, byte[] unitBuffer, IntFunction<ABIType<?>> getType, Object[] objects) {
        final int start = bb.position(); // save this value before offsets are decoded
        final int[] offsets = new int[objects.length];
        for(int i = 0; i < objects.length; i++) {
            ABIType<?> t = getType.apply(i);
            if(!t.dynamic) {
                objects[i] = t.decode(bb, unitBuffer);
            } else {
                offsets[i] = UINT31.decode(bb, unitBuffer);
            }
        }
        for (int i = 0; i < objects.length; i++) {
            final int offset = offsets[i];
            if(offset > 0) {
                final int jump = start + offset;
                final int pos = bb.position();
                if(jump != pos) {
                    /* LENIENT MODE; see https://github.com/ethereum/solidity/commit/3d1ca07e9b4b42355aa9be5db5c00048607986d1 */
                    if(jump < pos) {
                        throw new IllegalArgumentException("illegal backwards jump: (" + start + "+" + offset + "=" + jump + ")<" + pos);
                    }
                    bb.position(jump); // leniently jump to specified offset
                }
                objects[i] = getType.apply(i).decode(bb, unitBuffer);
            }
        }
    }

    /**
     * Parses RLP Object {@link com.esaulpaugh.headlong.rlp.util.Notation} as a {@link Tuple}.
     *
     * @param s the tuple's RLP object notation
     * @return  the parsed tuple
     * @see com.esaulpaugh.headlong.rlp.util.Notation
     */
    @Override
    public Tuple parseArgument(String s) { // expects RLP object notation
        return SuperSerial.deserialize(this, s, false);
    }

    /**
     * Gives the ABI encoding of the input values according to this {@link TupleType}'s element types.
     *
     * @param elements  values corresponding to this {@link TupleType}'s element types
     * @return  the encoding
     */
    public ByteBuffer encodeElements(Object... elements) {
        return encode(new Tuple(elements));
    }

    @Override
    public Iterator<ABIType<?>> iterator() {
        return Arrays.asList(elementTypes).iterator();
    }

    public TupleType subTupleType(boolean... manifest) {
        return subTupleType(manifest, false);
    }

    public TupleType subTupleTypeNegative(boolean... manifest) {
        return subTupleType(manifest, true);
    }

    private TupleType subTupleType(final boolean[] manifest, final boolean negate) {
        final int size = size();
        if(manifest.length == size) {
            final StringBuilder canonicalBuilder = new StringBuilder("(");
            boolean dynamic = false;
            final List<ABIType<?>> selected = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                if (negate ^ manifest[i]) {
                    ABIType<?> e = get(i);
                    canonicalBuilder.append(e.canonicalType).append(',');
                    dynamic |= e.dynamic;
                    selected.add(e);
                }
            }
            return new TupleType(completeTupleTypeString(canonicalBuilder), dynamic, selected.toArray(EMPTY_ARRAY));
        }
        throw new IllegalArgumentException("manifest.length != size(): " + manifest.length + " != " + size);
    }

    private static String completeTupleTypeString(StringBuilder sb) {
        final int len = sb.length();
        return len != 1
                ? sb.deleteCharAt(len - 1).append(')').toString() // replace trailing comma
                : EMPTY_TUPLE_STRING;
    }

    public static TupleType parse(String rawTupleTypeString) {
        return TypeFactory.create(rawTupleTypeString);
    }

    public static TupleType of(String... typeStrings) {
        StringBuilder sb = new StringBuilder("(");
        for (String str : typeStrings) {
            sb.append(str).append(',');
        }
        return parse(completeTupleTypeString(sb));
    }

    public static TupleType parseElements(String rawTypesList) {
        if(rawTypesList.endsWith(",")) {
            rawTypesList = rawTypesList.substring(0, rawTypesList.length() - 1);
        }
        return parse('(' + rawTypesList + ')');
    }
}
