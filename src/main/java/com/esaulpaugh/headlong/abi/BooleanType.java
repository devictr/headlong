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

import java.math.BigInteger;
import java.nio.ByteBuffer;

/** Unsigned 0 or 1. */
public final class BooleanType extends UnitType<Boolean> {

    private static final String ARRAY_CLASS_NAME = boolean[].class.getName();

    static final byte[] BOOLEAN_FALSE = new byte[UNIT_LENGTH_BYTES];
    static final byte[] BOOLEAN_TRUE = new byte[UNIT_LENGTH_BYTES];

    static {
        BOOLEAN_TRUE[BOOLEAN_TRUE.length-1] = 1;
    }

    BooleanType() {
        super("bool", Boolean.class, 1, true);
    }

    @Override
    String arrayClassName() {
        return ARRAY_CLASS_NAME;
    }

    @Override
    public int typeCode() {
        return TYPE_CODE_BOOLEAN;
    }

    @Override
    int byteLengthPacked(Object value) {
        return 1;
    }

    @Override
    public int validate(Object value) {
        validateClass(value);
        return UNIT_LENGTH_BYTES;
    }

    @Override
    int encodeHead(Object value, ByteBuffer dest, int offset) {
        dest.put((boolean) value ? BOOLEAN_TRUE : BOOLEAN_FALSE);
        return offset;
    }

    @Override
    Boolean decode(ByteBuffer bb, byte[] unitBuffer) {
        bb.get(unitBuffer);
        BigInteger bi = new BigInteger(unitBuffer);
        validateBigInt(bi);
        return decodeBoolean(bi.byteValue());
    }

    static Boolean decodeBoolean(byte b) {
        return b == Encoding.ZERO_BYTE ? Boolean.FALSE : Boolean.TRUE;
    }

    @Override
    public Boolean parseArgument(String s) {
        Boolean bool = Boolean.parseBoolean(s);
        validate(bool);
        return bool;
    }
}
