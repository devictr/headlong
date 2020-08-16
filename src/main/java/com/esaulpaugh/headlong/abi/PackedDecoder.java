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

import com.esaulpaugh.headlong.abi.util.Uint;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_ARRAY;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BIG_DECIMAL;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BIG_INTEGER;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BOOLEAN;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_BYTE;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_INT;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_LONG;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_TUPLE;
import static com.esaulpaugh.headlong.abi.ArrayType.DYNAMIC_LENGTH;

/**
 * Experimental. Unoptimized.
 */
public final class PackedDecoder {

    public static Tuple decode(TupleType types, byte[] buffer) {
        return decode(types, buffer, 0, buffer.length);
    }

    public static Tuple decode(TupleType tupleType, byte[] buffer, int from, int to) {
        if (countDynamicsTupleType(tupleType) <= 1) {
            final Tuple[] elements = new Tuple[1];
            decodeTuple(tupleType, buffer, from, to, elements, 0); // can also call decodeTupleStatic if numDynamic == 0
            final Tuple tuple = elements[0];
            tupleType.validate(tuple);
            return tuple;
        }
        throw new IllegalArgumentException("multiple dynamic elements");
    }

    private static int countDynamicsTupleType(TupleType tupleType) {
        int numDynamic = 0;
        for (ABIType<?> e : tupleType) {
            numDynamic += !e.dynamic
                    ? 0
                    : TYPE_CODE_TUPLE == e.typeCode()
                        ? countDynamicsTupleType((TupleType) e)
                        : countDynamicsArrayType(e); // assume ArrayType bc currently only TupleType and ArrayType can be dynamic
        }
        return numDynamic;
    }

    private static int countDynamicsArrayType(ABIType<?> type) {
        int numDynamic = 0;
        do {
            final ArrayType<? extends ABIType<?>, ?> arrayType = (ArrayType<? extends ABIType<?>, ?>) type;
            if(DYNAMIC_LENGTH == arrayType.length) {
                numDynamic++;
            }
            type = arrayType.elementType;
        } while (TYPE_CODE_ARRAY == type.typeCode()); // loop until type is the base type
        if(TYPE_CODE_TUPLE == type.typeCode()) {
            numDynamic += countDynamicsTupleType((TupleType) type);
        }
        return numDynamic;
    }

    private static int decodeTuple(TupleType tupleType, byte[] buffer, int start, int end, Object[] parentElements, int pei) {
        final ABIType<?>[] elementTypes = tupleType.elementTypes;
        final int len = elementTypes.length;
        final Object[] elements = new Object[len];

        int mark = -1;

        for (int i = len - 1; i >= 0; i--) {
            final ABIType<?> type = elementTypes[i];
            if (type.dynamic) {
                mark = i;
                break;
            }
            // static types only
            final int typeCode = type.typeCode();
            if(TYPE_CODE_ARRAY == typeCode) {
                final ArrayType<? extends ABIType<?>, ?> arrayType = (ArrayType<? extends ABIType<?>, ?>) type;
                end -= arrayType.elementType.byteLengthPacked(null) * arrayType.length;
                insertArray(arrayType, buffer, end, end, elements, i);
            } else if(TYPE_CODE_TUPLE == typeCode) {
                TupleType inner = (TupleType) type;
                int innerLen = inner.byteLengthPacked(null);
                end -= decodeTupleStatic(inner, buffer, end - innerLen, end, elements, i);
            } else {
                end -= decode(elementTypes[i], buffer, end - type.byteLengthPacked(null), end, elements, i);
            }
        }

        if (mark > -1) {
            for (int i = 0; i <= mark; i++) {
                start += decode(elementTypes[i], buffer, start, end, elements, i);
            }
        }
        return tupleType.byteLengthPacked(parentElements[pei] = new Tuple(elements));
    }

    private static int decode(ABIType<?> type, byte[] buffer, int idx, int end, Object[] elements, int i) {
        switch (type.typeCode()) {
        case TYPE_CODE_BOOLEAN: elements[i] = BooleanType.decodeBoolean(buffer[idx]); return type.byteLengthPacked(null);
        case TYPE_CODE_BYTE: elements[i] = buffer[idx]; return type.byteLengthPacked(null);
        case TYPE_CODE_INT: return insertInt((IntType) type, buffer, idx, type.byteLengthPacked(null), elements, i);
        case TYPE_CODE_LONG: return insertLong((LongType) type, buffer, idx, type.byteLengthPacked(null), elements, i);
        case TYPE_CODE_BIG_INTEGER: return insertBigInteger(type.byteLengthPacked(null), buffer, idx, elements, i, (BigIntegerType) type);
        case TYPE_CODE_BIG_DECIMAL: return insertBigDecimal(type.byteLengthPacked(null), buffer, idx, elements, i, (BigDecimalType) type);
        case TYPE_CODE_ARRAY: return insertArray((ArrayType<? extends ABIType<?>, ?>) type, buffer, idx, end, elements, i);
        case TYPE_CODE_TUPLE:
            return type.dynamic
                    ? decodeTuple((TupleType) type, buffer, idx, end, elements, i)
                    : decodeTupleStatic((TupleType) type, buffer, idx, end, elements, i);
        default: throw new Error();
        }
    }

    private static int decodeTupleStatic(TupleType tupleType, byte[] buffer, int idx, int end, Object[] parentElements, int pei) {
        final ABIType<?>[] elementTypes = tupleType.elementTypes;
        final int len = elementTypes.length;
        final Object[] elements = new Object[len];
        for (int i = 0; i < len; i++) {
            idx += decode(elementTypes[i], buffer, idx, end, elements, i);
        }
        return tupleType.byteLengthPacked(parentElements[pei] = new Tuple(elements));
    }

    private static int insertInt(UnitType<? extends Number> type, byte[] buffer, int idx, int len, Object[] dest, int destIdx) {
        dest[destIdx] = (int) decodeLong(type, buffer, idx, len);
        return len;
    }

    private static int insertLong(UnitType<? extends Number> type, byte[] buffer, int idx, int len, Object[] dest, int destIdx) {
        dest[destIdx] = decodeLong(type, buffer, idx, len);
        return len;
    }

    private static int insertBigInteger(int elementLen, byte[] buffer, int idx, Object[] dest, int destIdx, BigIntegerType type) {
        if(type.unsigned) {
            byte[] copy = new byte[1 + elementLen];
            System.arraycopy(buffer, idx, copy, 1, elementLen);
            dest[destIdx] = new BigInteger(copy);
        } else {
//            dest[destIdx] = new BigInteger(buffer, idx, elementLen); // Java 9+
            dest[destIdx] = new BigInteger(Arrays.copyOfRange(buffer, idx, idx + elementLen));
        }
        return elementLen;
    }

    private static int insertBigDecimal(int elementLen, byte[] buffer, int idx, Object[] dest, int destIdx, BigDecimalType type) {
        BigInteger unscaled;
        if(type.unsigned) {
            byte[] copy = new byte[1 + elementLen];
            System.arraycopy(buffer, idx, copy, 1, elementLen);
            unscaled = new BigInteger(copy);
        } else {
//            unscaled = new BigInteger(buffer, idx, elementLen); // Java 9+
            unscaled = new BigInteger(Arrays.copyOfRange(buffer, idx, idx + elementLen));
        }
        dest[destIdx] = new BigDecimal(unscaled, type.scale);
        return elementLen;
    }

    private static int insertArray(ArrayType<? extends ABIType<?>, ?> arrayType, byte[] buffer, int idx, int end, Object[] dest, int destIdx) {
        final ABIType<?> elementType = arrayType.elementType;
        final int elementByteLen = elementType.byteLengthPacked(null);
        final int arrayLen;
        if(arrayType.length == DYNAMIC_LENGTH) {
            if (elementByteLen == 0) {
                throw new IllegalArgumentException("can't decode dynamic number of zero-length elements");
            }
            arrayLen = (end - idx) / elementByteLen;
        } else {
            arrayLen = arrayType.length;
        }
        final Object array;
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: array = decodeBooleanArray(arrayLen, buffer, idx); break;
        case TYPE_CODE_BYTE: array = arrayType.encodeIfString(decodeByteArray(arrayLen, buffer, idx)); break;
        case TYPE_CODE_INT: array = decodeIntArray((IntType) elementType, elementByteLen, arrayLen, buffer, idx); break;
        case TYPE_CODE_LONG: array = decodeLongArray((LongType) elementType, elementByteLen, arrayLen, buffer, idx); break;
        case TYPE_CODE_BIG_INTEGER: array = decodeBigIntegerArray(elementByteLen, arrayLen, buffer, idx, (BigIntegerType) elementType); break;
        case TYPE_CODE_BIG_DECIMAL: array = decodeBigDecimalArray(elementByteLen, arrayLen, buffer, idx, (BigDecimalType) elementType); break;
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE: array = decodeObjectArray(arrayLen, elementType, buffer, idx, end); break;
        default: throw new Error();
        }
        dest[destIdx] = array;
        return arrayLen * elementByteLen;
    }

    private static boolean[] decodeBooleanArray(int arrayLen, byte[] buffer, int idx) {
        boolean[] booleans = new boolean[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            booleans[i] = BooleanType.decodeBoolean(buffer[idx + i]);
        }
        return booleans;
    }

    private static byte[] decodeByteArray(int arrayLen, byte[] buffer, int idx) {
        byte[] bytes = new byte[arrayLen];
        System.arraycopy(buffer, idx, bytes, 0, arrayLen);
        return bytes;
    }

    private static int[] decodeIntArray(IntType intType, int elementLen, int arrayLen, byte[] buffer, int idx) {
        long[] longs = decodeLongArray(intType, elementLen, arrayLen, buffer, idx);
        int[] ints = new int[arrayLen];
        for (int i = 0; i < longs.length; i++) {
            ints[i] = (int) longs[i];
        }
        return ints;
    }

    private static long[] decodeLongArray(UnitType<? extends Number> type, int elementLen, int arrayLen, byte[] buffer, int idx) {
        long[] longs = new long[arrayLen];
        if(type.unsigned) {
            Uint uint = new Uint(type.bitLength);
            for (int i = 0; i < arrayLen; i++) {
                longs[i] = decodeUnsignedLong(uint, buffer, idx, elementLen);
                idx += elementLen;
            }
        } else {
            for (int i = 0; i < arrayLen; i++) {
                longs[i] = decodeSignedLong(buffer, idx, elementLen);
                idx += elementLen;
            }
        }
        return longs;
    }

    private static BigInteger[] decodeBigIntegerArray(int elementLen, int arrayLen, byte[] buffer, int idx, BigIntegerType elementType) {
        BigInteger[] bigInts = new BigInteger[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            idx += insertBigInteger(elementLen, buffer, idx, bigInts, i, elementType);
        }
        return bigInts;
    }

    private static BigDecimal[] decodeBigDecimalArray(int elementLen, int arrayLen, byte[] buffer, int idx, BigDecimalType elementType) {
        BigDecimal[] bigDecimals = new BigDecimal[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            idx += insertBigDecimal(elementLen, buffer, idx, bigDecimals, i, elementType);
        }
        return bigDecimals;
    }

    private static Object[] decodeObjectArray(int arrayLen, ABIType<?> elementType, byte[] buffer, int idx, int end) {
        Object[] objects = (Object[]) Array.newInstance(elementType.clazz, arrayLen); // reflection ftw
        for (int i = 0; i < arrayLen; i++) {
            int len = decode(elementType, buffer, idx, end, objects, i);
            idx += len;
            end -= len;
        }
        return objects;
    }

    private static long decodeLong(UnitType<? extends Number> type, byte[] buffer, int idx, int len) {
        return type.unsigned
                ? decodeUnsignedLong(new Uint(type.bitLength), buffer, idx, len)
                : decodeSignedLong(buffer, idx, len);
    }

    private static long decodeUnsignedLong(Uint uint, byte[] buffer, int idx, int len) {
        long signed = decodeBigInteger(buffer, idx, len).longValue();
        return uint.toUnsignedLong(signed);
    }

    private static long decodeSignedLong(byte[] buffer, int idx, int len) {
        return decodeBigInteger(buffer, idx, len).longValue();
    }

    static BigInteger decodeBigInteger(byte[] buffer, int i, int len) {
//        return new BigInteger(buffer, i, len); // Java 9+
        return new BigInteger(Arrays.copyOfRange(buffer, i, i + len));
    }
}
