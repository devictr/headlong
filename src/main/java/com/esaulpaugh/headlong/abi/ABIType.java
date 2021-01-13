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

import com.esaulpaugh.headlong.util.Integers;
import com.esaulpaugh.headlong.util.Strings;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import static com.esaulpaugh.headlong.abi.UnitType.UNIT_LENGTH_BYTES;

/**
 * Represents a Contract ABI type such as uint256 or decimal. Used to validate, encode, and decode data.
 *
 * @param <J> this {@link ABIType}'s corresponding Java type
 */
public abstract class ABIType<J> {

    public static final int TYPE_CODE_BOOLEAN = 0;
    public static final int TYPE_CODE_BYTE = 1;
    public static final int TYPE_CODE_INT = 2;
    public static final int TYPE_CODE_LONG = 3;
    public static final int TYPE_CODE_BIG_INTEGER = 4;
    public static final int TYPE_CODE_BIG_DECIMAL = 5;

    public static final int TYPE_CODE_ARRAY = 6;
    public static final int TYPE_CODE_TUPLE = 7;

    public static final ABIType<?>[] EMPTY_TYPE_ARRAY = new ABIType<?>[0];

    final String canonicalType;
    final Class<J> clazz;
    final boolean dynamic;

    private String name = null;

    ABIType(String canonicalType, Class<J> clazz, boolean dynamic) {
        this.canonicalType = canonicalType; // .intern() to save memory and allow == comparison?
        this.clazz = clazz;
        this.dynamic = dynamic;
    }

    public final String getCanonicalType() {
        return canonicalType;
    }

    public final Class<J> clazz() {
        return clazz;
    }

    public final boolean isDynamic() {
        return dynamic;
    }

    public final String getName() {
        return name;
    }

    /* don't expose this; cached (nameless) instances are shared and must be immutable */
    final ABIType<J> setName(String name) {
        this.name = name;
        return this;
    }

    abstract Class<?> arrayClass() throws ClassNotFoundException;

    /**
     * Returns an integer code specific to this instance's class, which is a subclass of {@link ABIType}.
     *
     * @return the code
     */
    public abstract int typeCode();

    abstract int byteLength(Object value);

    abstract int byteLengthPacked(Object value);

    public final ByteBuffer encode(Object value) {
        validate(value);
        ByteBuffer dest = ByteBuffer.allocate(validate(value));
        encodeTail(value, dest);
        return dest;
    }

    /**
     * Checks whether the given object is a valid argument for this {@link ABIType}. Requires an instance of type J.
     *
     * @param value an object of type J
     * @return the byte length of the ABI encoding of {@code value}
     */
    public abstract int validate(Object value);

    int encodeHead(Object value, ByteBuffer dest, int nextOffset) {
        if (!dynamic) {
            encodeTail(value, dest);
            return nextOffset;
        }
        return Encoding.insertOffset(nextOffset, dest, byteLength(value));
    }

    abstract void encodeTail(Object value, ByteBuffer dest);

    public final J decode(byte[] array) {
        ByteBuffer bb = ByteBuffer.wrap(array);
        J decoded = decode(bb);
        final int remaining = bb.remaining();
        if(remaining == 0) {
            return decoded;
        }
        throw new IllegalArgumentException("unconsumed bytes: " + remaining + " remaining");
    }

    public final J decode(ByteBuffer buffer) {
        return decode(buffer, newUnitBuffer());
    }

    /**
     * Decodes the data at the buffer's current position according to this {@link ABIType}.
     *
     * @param buffer     the buffer containing the encoded data
     * @param unitBuffer a buffer of length {@link UnitType#UNIT_LENGTH_BYTES} in which to store intermediate values
     * @return the decoded value
     * @throws IllegalArgumentException if the data is malformed
     */
    abstract J decode(ByteBuffer buffer, byte[] unitBuffer);

    private static int[] decodeOffsets(int len, ByteBuffer bb, byte[] unitBuffer, Object[] elements, IntFunction<ABIType<?>> getType) {
        final int[] offsets = new int[len];
        for(int i = 0; i < len; i++) {
            ABIType<?> t = getType.apply(i);
            if(!t.dynamic) {
                elements[i] = t.decode(bb, unitBuffer);
            } else {
                offsets[i] = Encoding.UINT31.decode(bb, unitBuffer);
            }
        }
        return offsets;
    }

    static void decodeObjects(int len, ByteBuffer bb, byte[] unitBuffer, Object[] elements, IntFunction<ABIType<?>> getType) {
        final int start = bb.position(); // save this value before offsets are decoded
        final int[] offsets = decodeOffsets(len, bb, unitBuffer, elements, getType);
        for (int i = 0; i < len; i++) {
            final int offset = offsets[i];
            if(offset > 0) {
                if (offset >= 0x20) {
                    /* LENIENT MODE; see https://github.com/ethereum/solidity/commit/3d1ca07e9b4b42355aa9be5db5c00048607986d1 */
                    if (start + offset > bb.position()) {
                        bb.position(start + offset); // leniently jump to specified offset
                    }
                    try {
                        elements[i] = getType.apply(i).decode(bb, unitBuffer);
                    } catch (BufferUnderflowException bue) {
                        throw new IllegalArgumentException(bue);
                    }
                } else {
                    throw new IllegalArgumentException("offset less than 0x20");
                }
            }
        }
    }

    /**
     * Parses and validates a string representation of J.
     *
     * @param s the object's string representation
     * @return  the object
     */
    public abstract J parseArgument(String s);

    void validateClass(Object value) {
        if(!clazz.isInstance(value)) {
            if(value == null) {
                throw new NullPointerException();
            }
            throw new IllegalArgumentException("class mismatch: "
                    + value.getClass().getName()
                    + " not assignable to "
                    + clazz.getName()
                    + " (" + friendlyClassName(value.getClass()) + " not instanceof " + friendlyClassName(clazz) + "/" + canonicalType + ")");
        }
    }

    static byte[] newUnitBuffer() {
        return new byte[UNIT_LENGTH_BYTES];
    }

    @Override
    public final int hashCode() {
        return canonicalType.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof ABIType && ((ABIType<?>) o).canonicalType.equals(this.canonicalType);
    }

    @Override
    public final String toString() {
        return canonicalType;
    }

    public static String format(byte[] abi) {
        return format(abi, (row) -> {
            String unpadded = Integer.toHexString(row * UNIT_LENGTH_BYTES);
            return pad((LABEL_PADDED_LEN - LABEL_RIGHT_PADDING) - unpadded.length(), unpadded);
        });
    }

    public static String format(byte[] abi, RowLabeler labeler) {
        Integers.checkIsMultiple(abi.length, UNIT_LENGTH_BYTES);
        return finishFormat(abi, 0, abi.length, labeler, new StringBuilder());
    }

    static String finishFormat(byte[] buffer, int offset, int end, RowLabeler labeler, StringBuilder sb) {
        int row = 0;
        while(offset < end) {
            if(offset > 0) {
                sb.append('\n');
            }
            sb.append(labeler.paddedLabel(row++))
                    .append(Strings.encode(buffer, offset, UNIT_LENGTH_BYTES, Strings.HEX));
            offset += UNIT_LENGTH_BYTES;
        }
        return sb.toString();
    }

    @FunctionalInterface
    public interface RowLabeler {
        String paddedLabel(int row);
    }

    private static final int LABEL_PADDED_LEN = 9;
    private static final int LABEL_RIGHT_PADDING = 3;

    static String pad(int leftPadding, String unpadded) {
        StringBuilder sb = new StringBuilder();
        pad(sb, leftPadding);
        sb.append(unpadded);
        pad(sb, LABEL_PADDED_LEN - sb.length());
        return sb.toString();
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) {
            sb.append(' ');
        }
    }

    static String friendlyClassName(Class<?> clazz) {
        return friendlyClassName(clazz, null);
    }

    static String friendlyClassName(Class<?> clazz, Integer arrayLength) {
        final String className = clazz.getName();
        final int split = className.lastIndexOf('[') + 1;
        final boolean hasArraySuffix = split > 0;
        final StringBuilder sb = new StringBuilder();
        final String base = hasArraySuffix ? className.substring(split) : className;
        switch (base) {
            case "B": sb.append("byte"); break;
            case "S": sb.append("short"); break;
            case "I": sb.append("int"); break;
            case "J": sb.append("long"); break;
            case "F": sb.append("float"); break;
            case "D": sb.append("double"); break;
            case "C": sb.append("char"); break;
            case "Z": sb.append("boolean"); break;
            default: {
                int lastDotIndex = base.lastIndexOf('.');
                if(lastDotIndex != -1) {
                    sb.append(base, lastDotIndex + 1, base.length() - (base.charAt(0) == 'L' ? 1 : 0));
                }
            }
        }
        if(hasArraySuffix) {
            int i = 0;
            if(arrayLength != null && arrayLength >= 0) {
                sb.append('[').append(arrayLength).append(']');
                i++;
            }
            while (i++ < split) {
                sb.append("[]");
            }
        }
        return sb.toString();
    }
}
