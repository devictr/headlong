package com.esaulpaugh.headlong.abi;

import com.esaulpaugh.headlong.TestUtils;
import com.esaulpaugh.headlong.abi.util.WrappedKeccak;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FunctionTest {

    @Test
    public void testFormatLength() {
        int len = 4 + 32;
        byte[] buffer = new byte[len + 103];
        for (int i = 0; i < 33; i++) {
            assertEquals(
                    "ID       00000000\n" +
                    "0        0000000000000000000000000000000000000000000000000000000000000000",
                    Function.formatCall(buffer, i, len)
            );
        }
    }

    @Test
    public void testFunctionValidation() throws Throwable {
        final Class<? extends Throwable> err = IllegalArgumentException.class;
        final TupleType inputs = TupleType.of("int");
        final TupleType outputs = TupleType.of("bool");
        final MessageDigest md = Function.newDefaultDigest();
        TestUtils.assertThrown(err, "type is \"constructor\"; functions of this type must define no outputs", () -> new Function(Function.Type.CONSTRUCTOR, "foo()","(bool)", md));
        TestUtils.assertThrown(err, "type is \"fallback\"; functions of this type must define no outputs", () -> new Function(Function.Type.FALLBACK, "foo()","(bool)", md));
        TestUtils.assertThrown(err, "type is \"constructor\"; functions of this type must not define name", () -> new Function(Function.Type.CONSTRUCTOR, "foo()","()", md));
        TestUtils.assertThrown(err, "type is \"fallback\"; functions of this type must not define name", () -> new Function(Function.Type.FALLBACK, "foo()","()", md));
        Function f = new Function(Function.Type.CONSTRUCTOR, "()","()", md);
        assertNull(f.getName());
        assertEquals(TupleType.EMPTY, f.getParamTypes());
        assertEquals(TupleType.EMPTY, f.getOutputTypes());
        f = new Function(Function.Type.FALLBACK, "()","()", md);
        assertEquals(Function.Type.FALLBACK, f.getType());
        assertNull(f.getName());
        assertEquals("Keccak-256", f.getHashAlgorithm());

        TestUtils.assertThrown(err, "type is \"receive\"; functions of this type must define stateMutability as \"payable\"", () -> new Function(Function.Type.RECEIVE, "receive", inputs, outputs, null, md));
        TestUtils.assertThrown(err, "type is \"receive\"; functions of this type must define no inputs", () -> new Function(Function.Type.RECEIVE, "receive", inputs, outputs, "payable", md));
        TestUtils.assertThrown(err, "type is \"receive\"; functions of this type must define no outputs", () -> new Function(Function.Type.RECEIVE, "receive", TupleType.EMPTY, outputs, "payable", md));
        f = new Function(Function.Type.RECEIVE, "receive", TupleType.EMPTY, TupleType.EMPTY, "payable", new WrappedKeccak(256));
        assertEquals("receive", f.getName());
        assertEquals("payable", f.getStateMutability());
        assertEquals("Keccak-256", f.getHashAlgorithm());

        TestUtils.assertThrown(err, "type is \"function\"; functions of this type must define name", () -> new Function(Function.Type.FUNCTION, null, TupleType.EMPTY, TupleType.EMPTY, null, md));
        f = new Function(Function.Type.FUNCTION, "", TupleType.EMPTY, TupleType.EMPTY, null, md);
        assertEquals("", f.getName());
        assertNull(f.getStateMutability());

        TestUtils.assertThrown(err, "illegal char 0x28 '(' @ index 0", () -> new Function(Function.Type.FUNCTION, "(", inputs, outputs, null, md));
        TestUtils.assertThrown(err, "illegal char 0x28 '(' @ index 1", () -> new Function(Function.Type.FUNCTION, "a(", inputs, outputs, null, md));
        TestUtils.assertThrown(err, "illegal char 0x28 '(' @ index 0", () -> new Function(Function.Type.FUNCTION, "(b", inputs, outputs, null, md));
        TestUtils.assertThrown(err, "illegal char 0x28 '(' @ index 1", () -> new Function(Function.Type.FUNCTION, "c(d", inputs, outputs, null, md));
        TestUtils.assertThrown(err, "illegal char 0x256 '\u0256' @ index 0", () -> new Function(Function.Type.FUNCTION, "\u0256", inputs, outputs, null, md));
        new Function(Function.Type.FUNCTION, "z", inputs, outputs, null, md);
        new Function(Function.Type.FUNCTION, "", inputs, outputs, null, md);
    }

    @Test
    public void testNonCanonicalEquals() {

        testNonCanonicalEquals("foo(int256)",           "foo(int)");
        testNonCanonicalEquals("foo(int256[])",         "foo(int[])");
        testNonCanonicalEquals("foo(int256[31])",       "foo(int[31])");
        testNonCanonicalEquals("foo(int256[][])",       "foo(int[][])");
        testNonCanonicalEquals("foo(int256[][7])",      "foo(int[][7])");
        testNonCanonicalEquals("foo(int256[5][])",      "foo(int[5][])");
        testNonCanonicalEquals("foo(int256[100][100])", "foo(int[100][100])");

        testNonCanonicalEquals("foo(uint256)",          "foo(uint)");
        testNonCanonicalEquals("foo(uint256[])",        "foo(uint[])");
        testNonCanonicalEquals("foo(uint256[31])",      "foo(uint[31])");
        testNonCanonicalEquals("foo(uint256[][])",      "foo(uint[][])");
        testNonCanonicalEquals("foo(uint256[][7])",     "foo(uint[][7])");
        testNonCanonicalEquals("foo(uint256[5][])",     "foo(uint[5][])");
        testNonCanonicalEquals("foo(uint256[100][100])","foo(uint[100][100])");
    }

    private static void testNonCanonicalEquals(String canonical, String nonCanonical) {
        assertNotEquals(canonical, nonCanonical);
        Function canon = Function.parse(canonical);
        Function nonCanon = Function.parse(nonCanonical);
        assertEquals(canon, nonCanon);
        assertEquals(canon.getCanonicalSignature(), nonCanon.getCanonicalSignature());
    }

    @Test
    public void testFormatTupleType() {
        String f = Function.formatCall(new byte[] { 1, 1, 1, 1, 0x45, 0x13, 0x79, 0x03,
                34, 33, 32, 31,
                34, 33, 32, 31,
                34, 33, 32, 31,
                34, 33, 32, 31,
                34, 33, 32, 31,
                34, 33, 32, 31,
                34, 33, 32, 31,
                34, 33, 32, 31,
        }, 4, 36);
        System.out.println(f);
        assertEquals("ID       45137903\n0        2221201f2221201f2221201f2221201f2221201f2221201f2221201f2221201f", f);
    }
}
