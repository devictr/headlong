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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Uses a larger encoding table to speed up encoding. */
public final class FastHex {

    private FastHex() {}

    private static final int CHARS_PER_BYTE = 2;

    private static final int BITS_PER_CHAR = Byte.SIZE / CHARS_PER_BYTE;

    // Byte values index directly into the encoding table (size 256) whose elements contain two char values each,
    // encoded together as an int.
    private static final short[] ENCODE_TABLE = new short[1 << Byte.SIZE];

    // Char values index directly into the decoding table (size 256).
    private static final byte[] DECODE_TABLE = new byte[1 << Byte.SIZE];

    private static final byte NO_MAPPING = -1;

    static {
        final char[] chars = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
        final int leftNibbleMask = 0xF0;
        final int rightNibbleMask = 0x0F;
        for (int i = 0; i < ENCODE_TABLE.length; i++) {
            char leftChar = chars[(i & leftNibbleMask) >>> BITS_PER_CHAR];
            char rightChar = chars[i & rightNibbleMask];
            ENCODE_TABLE[i] = (short) ((leftChar << Byte.SIZE) | rightChar);
        }

        Arrays.fill(DECODE_TABLE, NO_MAPPING);

        DECODE_TABLE['0'] = 0x00;
        DECODE_TABLE['1'] = 0x01;
        DECODE_TABLE['2'] = 0x02;
        DECODE_TABLE['3'] = 0x03;
        DECODE_TABLE['4'] = 0x04;
        DECODE_TABLE['5'] = 0x05;
        DECODE_TABLE['6'] = 0x06;
        DECODE_TABLE['7'] = 0x07;
        DECODE_TABLE['8'] = 0x08;
        DECODE_TABLE['9'] = 0x09;
        DECODE_TABLE['A'] = DECODE_TABLE['a'] = 0xa;
        DECODE_TABLE['B'] = DECODE_TABLE['b'] = 0xb;
        DECODE_TABLE['C'] = DECODE_TABLE['c'] = 0xc;
        DECODE_TABLE['D'] = DECODE_TABLE['d'] = 0xd;
        DECODE_TABLE['E'] = DECODE_TABLE['e'] = 0xe;
        DECODE_TABLE['F'] = DECODE_TABLE['f'] = 0xf;
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

    public static byte[] decode(String hex, int offset, int len) {
        return decode(hex.getBytes(StandardCharsets.US_ASCII), offset, len);
    }

    public static byte[] decode(byte[] hexBytes, int offset, int len) {
        if (Integers.mod(len, CHARS_PER_BYTE) == 0) {
            final int bytesLen = len / CHARS_PER_BYTE;
            final byte[] bytes = new byte[bytesLen];
            for (int i = 0; i < bytesLen; i++, offset += CHARS_PER_BYTE) {
                byte left = DECODE_TABLE[hexBytes[offset]];
                byte right = DECODE_TABLE[hexBytes[offset+1]];
                if (left == NO_MAPPING || right == NO_MAPPING) {
                    throw new IllegalArgumentException("illegal hex val @ " + (left == NO_MAPPING ? offset : offset + 1));
                }
                bytes[i] = (byte) ((left << BITS_PER_CHAR) | right);
            }
            return bytes;
        }
        throw new IllegalArgumentException("len must be a multiple of two");
    }
}
