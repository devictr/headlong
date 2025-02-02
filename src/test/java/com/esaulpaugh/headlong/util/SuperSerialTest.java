/*
   Copyright 2020 Evan Saulpaugh

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

import com.esaulpaugh.headlong.TestUtils;
import com.esaulpaugh.headlong.abi.BooleanType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.IntType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SuperSerialTest {

    @Test
    public void testSuperSerial() throws Throwable {
        // java -jar headlong-cli-0.2-SNAPSHOT.jar -e "(uint[],int[],uint32,(int32,uint8,(bool[],int8,int40,int64,int,int,int[]),bool,bool,int256[]),int,int)" "([  ], [ '' ], '80', ['7f', '3b', [ [  ], '', '', '30ffcc0009', '01', '02', [ '70' ] ], '', '01', ['0092030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2020']], '', '05')"

        TestUtils.assertThrown(IllegalArgumentException.class, "integer data cannot exceed 32 bytes",
                () -> SuperSerial.deserialize(TupleType.parse("(int256)"), "('0092030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2020')", false)
        );

        SuperSerial.deserialize(TupleType.parse("(uint256)"), "('0092030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2020')", false);

        TestUtils.assertThrown(IllegalArgumentException.class, "RLPList not allowed for this type: int8",
                () -> SuperSerial.deserialize(TupleType.of("int8"), "(['90', '80', '77'])", false)
        );

        String sig = "(uint[],int[],uint32,(int32,uint8,(bool[],int8,int40,int64,int,int,int[]),bool,bool,int256[]),int,int)";

        Function f = new Function(sig);

        String vals = "([  ], [ '' ], '80', ['7f', '3b', [ [  ], '', '', '30ffcc0009', '01', '02', [ '70' ] ], '', '01', ['92030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2020']], '', '05')";

        Tuple decoded = SuperSerial.deserialize(f.getInputs(), vals, false);

        f.getInputs().validate(decoded);

        ByteBuffer bb = f.getInputs().encode(decoded);

        Tuple dd = f.getInputs().decode(bb);

        assertEquals(decoded, dd);
    }

    @Test
    public void testParseArgs() throws Throwable {

        String boolArrStr = "['00', '01', '01']";

        TupleType tt = TupleType.parse("(bool[])");

        Tuple tuple = tt.parseArgument("(\n" + boolArrStr + "\n)");

        boolean[] arr0 = tuple.get(0);

        assertArrayEquals(new boolean[] { false, true, true}, arr0);

        boolean[] arr1 = (boolean[]) tt.get(0).parseArgument(boolArrStr);

        assertArrayEquals(arr0, arr1);

        IntType int32 = (IntType) TupleType.parse("(int32)").get(0);

        assertEquals(Integer.MIN_VALUE, int32.parseArgument(Integer.toString(Integer.MIN_VALUE)));
        assertEquals(Integer.MAX_VALUE, int32.parseArgument(Integer.toString(Integer.MAX_VALUE)));

        IntType int8 = (IntType) TupleType.parse("(int8)").get(0);

        TestUtils.assertThrown(IllegalArgumentException.class, "signed val exceeds bit limit: 8 >= 8", () -> int8.parseArgument("-129"));
        TestUtils.assertThrown(IllegalArgumentException.class, "signed val exceeds bit limit: 8 >= 8", () -> int8.parseArgument("128"));

        BooleanType bool = (BooleanType) TupleType.parse("(bool)").get(0);

        assertEquals(true, bool.parseArgument("true"));
        assertEquals(true, bool.parseArgument("TRUE"));
        assertEquals(true, bool.parseArgument("tRUe"));
        assertEquals(false, bool.parseArgument("false"));
        assertEquals(false, bool.parseArgument(""));
        assertEquals(false, bool.parseArgument("?\t*jjgHJge"));

        assertEquals(127, int8.parseArgument("127"));
        assertEquals(127, int8.parseArgument("0127"));
        assertEquals(127, int8.parseArgument("00127"));
    }
}
