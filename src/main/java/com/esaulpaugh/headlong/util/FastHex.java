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
package com.esaulpaugh.headlong.util;

/** Uses a larger encoding table to speed up encoding. */
public final class FastHex {

    private FastHex() {}

    private static final int CHARS_PER_BYTE = 2;

    private static final int BITS_PER_CHAR = Byte.SIZE / CHARS_PER_BYTE;

    // Byte values index directly into the encoding table (size 256) whose elements contain two char values each,
    // encoded together as an int.
    private static final short[] ENCODE_TABLE = new short[1 << Byte.SIZE];

    static {
        final char[] chars = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
        final int leftNibbleMask = 0xF0;
        final int rightNibbleMask = 0x0F;
        for (int i = 0; i < ENCODE_TABLE.length; i++) {
            char leftChar = chars[(i & leftNibbleMask) >>> BITS_PER_CHAR];
            char rightChar = chars[i & rightNibbleMask];
            ENCODE_TABLE[i] = (short) ((leftChar << Byte.SIZE) | rightChar);
        }
    }

    public static String encodeToString(byte... buffer) {
        return encodeToString(buffer, 0, buffer.length);
    }

    @SuppressWarnings("deprecation")
    public static String encodeToString(byte[] buffer, int offset, int len) {
        byte[] enc = encodeToBytes(buffer, offset, len);
        return new String(enc, 0, 0, enc.length); // faster on Java 9+ (compact strings on by default)
    }

    public static byte[] encodeToBytes(byte[] buffer, int offset, int len) {
        final int end = offset + len;
        byte[] bytes = new byte[len * CHARS_PER_BYTE];
        for (int j = 0; offset < end; offset++, j += CHARS_PER_BYTE) {
            int hexPair = ENCODE_TABLE[buffer[offset] & 0xFF];
            bytes[j] = (byte) (hexPair >>> Byte.SIZE); // left
            bytes[j+1] = (byte) hexPair; // right
        }
        return bytes;
    }

    public static byte[] decode(String hex) {
        return decode(hex, 0, hex.length());
    }

    @FunctionalInterface
    private interface BiIntConsumer {
        void decode(int a, int b);
    }

    public static byte[] decode(String hex, int offset, int len) {
        final byte[] bytes = new byte[len / CHARS_PER_BYTE];
        return decode(offset, len, bytes, (i, o) -> bytes[i] = (byte) decodeBytes((byte) hex.charAt(o), (byte) hex.charAt(o+1), o));
    }

    public static byte[] decode(byte[] hexBytes, int offset, int len) {
        final byte[] bytes = new byte[len / CHARS_PER_BYTE];
        return decode(offset, len, bytes, (i, o) -> bytes[i] = (byte) decodeBytes(hexBytes[o], hexBytes[o+1], o));
    }

    private static byte[] decode(int offset, final int len, final byte[] dest, final BiIntConsumer decoder) {
        if (Integers.mod(len, CHARS_PER_BYTE) != 0) {
            throw new IllegalArgumentException("len must be a multiple of two");
        }
        for (int i = 0; i < dest.length; i++) {
            decoder.decode(i, offset);
            offset += 2;
        }
        return dest;
    }

    private static int decodeBytes(byte a, byte b, int offset) {
        return ((decodeByte(a, offset, 0) << BITS_PER_CHAR) | decodeByte(b, offset, 1));
    }

    private static int decodeByte(final byte c, int offset, int offsetDelta) {
        if(c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 0xa);
        }
        if (c >= 'A' && c <= 'F') {
            return  c - ('A' - 0xA);
        }
        throw new IllegalArgumentException("illegal hex val @ " + (offset + offsetDelta));
    }
}
