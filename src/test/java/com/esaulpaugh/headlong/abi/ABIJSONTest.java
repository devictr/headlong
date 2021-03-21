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

import com.esaulpaugh.headlong.TestUtils;
import com.esaulpaugh.headlong.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_ARRAY;
import static com.esaulpaugh.headlong.abi.ABIType.TYPE_CODE_TUPLE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ABIJSONTest {

    private static final String FUNCTION_A_JSON = "{\n" +
            "  \"type\": \"function\",\n" +
            "  \"name\": \"foo\",\n" +
            "  \"inputs\": [\n" +
            "    {\n" +
            "      \"name\": \"complex_nums\",\n" +
            "      \"type\": \"tuple[][]\",\n" +
            "      \"components\": [\n" +
            "        {\n" +
            "          \"name\": \"real\",\n" +
            "          \"type\": \"decimal\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"name\": \"imaginary\",\n" +
            "          \"type\": \"decimal\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"outputs\": [\n" +
            "    {\n" +
            "      \"name\": \"count\",\n" +
            "      \"type\": \"uint64\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"constant\": false\n" +
            "}";

    private static final String FUNCTION_B_JSON = "{\n" +
            "  \"type\": \"function\",\n" +
            "  \"name\": \"func\",\n" +
            "  \"inputs\": [\n" +
            "    {\n" +
            "      \"name\": \"aa\",\n" +
            "      \"type\": \"tuple\",\n" +
            "      \"components\": [\n" +
            "        {\n" +
            "          \"name\": \"aa_d\",\n" +
            "          \"type\": \"decimal\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"name\": \"aa_f\",\n" +
            "          \"type\": \"fixed128x18\"\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"bb\",\n" +
            "      \"type\": \"fixed128x18[]\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"cc\",\n" +
            "      \"type\": \"tuple\",\n" +
            "      \"components\": [\n" +
            "        {\n" +
            "          \"name\": \"cc_uint\",\n" +
            "          \"type\": \"uint256\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"name\": \"cc_int_arr\",\n" +
            "          \"type\": \"int256[]\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"name\": \"cc_tuple_arr\",\n" +
            "          \"type\": \"tuple[]\",\n" +
            "          \"components\": [\n" +
            "            {\n" +
            "              \"type\": \"int8\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"type\": \"uint40\"\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"outputs\": [],\n" +
            "  \"stateMutability\": \"view\",\n" +
            "  \"constant\": true\n" +
            "}";

    private static final String CONTRACT_JSON = "[\n" +
            "  {\n" +
            "    \"type\": \"event\",\n" +
            "    \"name\": \"an_event\",\n" +
            "    \"inputs\": [\n" +
            "      {\n" +
            "        \"name\": \"a\",\n" +
            "        \"type\": \"bytes\",\n" +
            "        \"indexed\": true\n" +
            "      },\n" +
            "      {\n" +
            "        \"name\": \"b\",\n" +
            "        \"type\": \"uint256\",\n" +
            "        \"indexed\": false\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"type\": \"function\",\n" +
            "    \"name\": \"\",\n" +
            "    \"inputs\": [\n" +
            "      {\n" +
            "        \"name\": \"aa\",\n" +
            "        \"type\": \"tuple\",\n" +
            "        \"components\": [\n" +
            "          {\n" +
            "            \"name\": \"aa_d\",\n" +
            "            \"type\": \"decimal\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"name\": \"aa_f\",\n" +
            "            \"type\": \"fixed128x18\"\n" +
            "          }\n" +
            "        ]\n" +
            "      },\n" +
            "      {\n" +
            "        \"name\": \"bb\",\n" +
            "        \"type\": \"fixed128x18[]\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"name\": \"cc\",\n" +
            "        \"type\": \"tuple\",\n" +
            "        \"components\": [\n" +
            "          {\n" +
            "            \"name\": \"cc_uint\",\n" +
            "            \"type\": \"uint256\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"name\": \"cc_int_arr\",\n" +
            "            \"type\": \"int256[]\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"name\": \"cc_tuple_arr\",\n" +
            "            \"type\": \"tuple[]\",\n" +
            "            \"components\": [\n" +
            "              {\n" +
            "                \"name\": \"cc_tuple_arr_int_eight\",\n" +
            "                \"type\": \"int8\"\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"cc_tuple_arr_uint_forty\",\n" +
            "                \"type\": \"uint40\"\n" +
            "              }\n" +
            "            ]\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    ],\n" +
            "    \"outputs\": [],\n" +
            "    \"constant\": false\n" +
            "  }\n" +
            "]";

    private static final String FALLBACK_CONSTRUCTOR_RECEIVE = "[\n" +
            "  {\n" +
            "    \"type\": \"fallback\",\n" +
            "    \"stateMutability\": \"pure\",\n" +
            "    \"constant\": true\n" +
            "  },\n" +
            "  {\n" +
            "    \"type\": \"constructor\",\n" +
            "    \"inputs\": [\n" +
            "      {\n" +
            "        \"name\": \"aha\",\n" +
            "        \"type\": \"bool\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"stateMutability\": \"nonpayable\",\n" +
            "    \"constant\": false\n" +
            "  },\n" +
            "  {\n" +
            "    \"type\": \"receive\",\n" +
            "    \"name\": \"receive\",\n" +
            "    \"stateMutability\": \"payable\",\n" +
            "    \"constant\": false\n" +
            "  }\n" +
            "]";

    private static void toString(ABIType<?> type, StringBuilder sb) {
        switch (type.typeCode()) {
        case TYPE_CODE_ARRAY:
            sb.append('[');
            toString(((ArrayType<? extends ABIType<?>, ?>) type).getElementType(), sb);
            sb.append(']');
            break;
        case TYPE_CODE_TUPLE:
            sb.append('(');
            for(ABIType<?> e : (TupleType) type) {
                toString(e, sb);
            }
            sb.append(')');
            break;
        default:
            sb.append(type);
        }
        sb.append(' ').append(type.getName()).append(',');
    }

    private static void printTupleType(TupleType tupleType) {
        StringBuilder sb = new StringBuilder();
        toString(tupleType, sb);
        System.out.println("RECURSIVE = " + sb.toString());
    }

    @Test
    public void testToJson() {

        String[] jsons = new String[7];

        int i = 0;
        jsons[i++] = FUNCTION_A_JSON;
        jsons[i++] = FUNCTION_B_JSON;
        JsonArray contractArray = JsonUtils.parseArray(CONTRACT_JSON);
        final int n = contractArray.size();
        for (int j = 0; j < n; j++) {
            jsons[i++] = JsonUtils.toPrettyPrint(contractArray.get(j).getAsJsonObject());
        }
        JsonArray fallbackEtc = JsonUtils.parseArray(FALLBACK_CONSTRUCTOR_RECEIVE);
        final int n2 = fallbackEtc.size();
        for (int j = 0; j < n2; j++) {
            jsons[i++] = JsonUtils.toPrettyPrint(fallbackEtc.get(j).getAsJsonObject());
        }

        for (String originalJson : jsons) {
            ABIObject orig = ABIJSON.parseABIObject(JsonUtils.parseObject(originalJson));
            String newJson = orig.toJson(false);
            assertNotEquals(originalJson, newJson);

            ABIObject reconstructed = ABIJSON.parseABIObject(JsonUtils.parseObject(newJson));

            assertEquals(orig, reconstructed);
            assertEquals(originalJson, reconstructed.toString());

            if(orig instanceof Function) {
                assertEquals(orig, ABIJSON.parseFunction(newJson));
            } else {
                assertEquals(orig, ABIJSON.parseEvent(newJson));
            }
        }
    }

    @Test
    public void testParseFunctionA() {
        final Function f = Function.fromJson(FUNCTION_A_JSON);
        final TupleType in = f.getInputs();
        final TupleType out = f.getOutputs();
        final ABIType<?> out0 = out.get(0);

        System.out.println(f.getName() + " : " + f.getCanonicalSignature() + " : " + out0);
        assertEquals(1, in.elementTypes.length);
        assertEquals(1, out.elementTypes.length);

        assertEquals("foo((decimal,decimal)[][])", f.getCanonicalSignature());
        assertEquals("uint64", out0.getCanonicalType());

        assertFalse(out0.isDynamic());
        assertNull(f.getStateMutability());
        f.encodeCallWithArgs((Object) new Tuple[][] { new Tuple[] { new Tuple(new BigDecimal(BigInteger.ONE, 10), new BigDecimal(BigInteger.TEN, 10)) } });

        printTupleType(in);
        printTupleType(out);
    }

    @Test
    public void testParseFunctionB() {
        final Function f = Function.fromJson(FUNCTION_B_JSON);
        System.out.println(f.getName() + " : " + f.getCanonicalSignature());
        assertEquals(TupleType.EMPTY, f.getOutputs());
        assertEquals("func((decimal,fixed128x18),fixed128x18[],(uint256,int256[],(int8,uint40)[]))", f.getCanonicalSignature());
        assertEquals("view", f.getStateMutability());

        printTupleType(f.getInputs());
    }

    @Test
    public void testParseFunction2() throws Throwable {
        final JsonObject function = new JsonObject();

        TestUtils.CustomRunnable parse = () -> Function.fromJsonObject(function);

        TestUtils.assertThrown(IllegalArgumentException.class, "type is \"function\"; functions of this type must define name", parse);

        function.add("type", new JsonPrimitive("event"));

        TestUtils.assertThrown(IllegalArgumentException.class, "unexpected type: \"event\"", parse);

        function.add("type", new JsonPrimitive("function"));

        TestUtils.assertThrown(IllegalArgumentException.class, "type is \"function\"; functions of this type must define name", parse);

        TestUtils.CustomRunnable[] updates = new TestUtils.CustomRunnable[] {
                () -> function.add("type", new JsonPrimitive("fallback")),
                () -> function.add("type", new JsonPrimitive("constructor")),
                () -> function.add("inputs", new JsonArray()),
                () -> {
                    function.remove("inputs");
                    function.add("name", new JsonPrimitive(""));
                    function.add("type", new JsonPrimitive("function"));
                }
        };

        for(TestUtils.CustomRunnable update : updates) {
            update.run();
            parse.run();
        }
    }

    @Test
    public void testParseEvent() throws Throwable {
        JsonObject jsonObject = new JsonObject();

        TestUtils.CustomRunnable runnable = () -> Event.fromJsonObject(jsonObject);

        TestUtils.assertThrown(IllegalArgumentException.class, "unexpected type: null", runnable);

        jsonObject.add("type", new JsonPrimitive("event"));

        TestUtils.assertThrown(IllegalArgumentException.class, "array \"inputs\" null or not found", runnable);

        jsonObject.add("inputs", new JsonArray());

        TestUtils.assertThrown(NullPointerException.class, runnable);

        jsonObject.add("name", new JsonPrimitive("a_name"));

        runnable.run();

        Event expectedA = Event.create("a_name", TupleType.parse("()"));
        Event expectedB = Event.create("a_name", TupleType.EMPTY);
        assertEquals(expectedA, expectedB);
        assertEquals(expectedA.hashCode(), expectedB.hashCode());

        assertEquals(expectedA, Event.fromJson(jsonObject.toString()));
    }

    @Test
    public void testAnonymousEvent() {
        TupleType inputs1 = TupleType.parse("(bool[],int,(uint32,string)[])");
        TupleType inputs2 = TupleType.parse(inputs1.canonicalType);
        TupleType inputs3 = Function.parse("foo(bool[],int,(uint32,string)[])").getInputs();
        Event a = Event.createAnonymous("x17", inputs1, true, false, true);
        Event b = Event.createAnonymous("x17", inputs2, true, false, true);
        Event c = new Event("x17", true, inputs3, true, false, true);
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.hashCode(), c.hashCode());
    }

    @Test
    public void testGetFunctions() {

        List<Function> functions;

        functions = ABIJSON.parseFunctions(CONTRACT_JSON);

        assertEquals(1, functions.size());

        {
            List<Function> f2 = ABIJSON.parseFunctions(CONTRACT_JSON.replace("    \"type\": \"function\",\n", ""));
            assertEquals(1, f2.size());
            assertEquals(functions.get(0), f2.get(0));
        }

        Function func = functions.get(0);

        printTupleType(func.getInputs());

        assertEquals(TypeEnum.FUNCTION, func.getType());
        assertEquals("", func.getName());
        assertNull(func.getStateMutability());

        functions = ABIJSON.parseFunctions(FALLBACK_CONSTRUCTOR_RECEIVE);

        assertEquals(3, functions.size());

        assertNull(functions.get(0).getName());
        assertNull(functions.get(1).getName());

        for(Function x : functions) {
            printTupleType(x.getInputs());
            assertEquals(TupleType.EMPTY, x.getOutputs());
        }

        Function fallback = functions.get(0);
        Function constructor = functions.get(1);

        assertEquals(TypeEnum.FALLBACK, fallback.getType());
        assertEquals(TupleType.EMPTY, fallback.getInputs());
        assertEquals(TupleType.EMPTY, fallback.getOutputs());
        assertEquals("pure", fallback.getStateMutability());

        assertEquals(TypeEnum.CONSTRUCTOR, constructor.getType());
        assertEquals(TupleType.parse("(bool)"), constructor.getInputs());
        assertEquals(TupleType.EMPTY, fallback.getOutputs());
        assertEquals("nonpayable", constructor.getStateMutability());
    }

    @Test
    public void testGetEvents() {
        List<Event> events = ABIJSON.parseEvents(CONTRACT_JSON);

        assertEquals(1, events.size());

        Event event = events.get(0);

        assertEquals("an_event", event.getName());
        assertEquals(TupleType.parse("(bytes,uint256)"), event.getInputs());
        assertEquals(TupleType.parse("(bytes)"), event.getIndexedParams());
        assertEquals(TupleType.parse("(uint256)"), event.getNonIndexedParams());
        assertArrayEquals(new boolean[] { true, false }, event.getIndexManifest());

        assertEquals("a", event.getInputs().get(0).getName());
        assertEquals("b", event.getInputs().get(1).getName());

        assertEquals("a", event.getIndexedParams().get(0).getName());
        assertEquals("b", event.getNonIndexedParams().get(0).getName());
    }

    @Test
    public void testJsonUtils() {
        JsonObject empty = new JsonObject();
        Boolean b = JsonUtils.getBoolean(empty, "constant");
        assertNull(b);
        Boolean b2 = JsonUtils.getBoolean(empty, "constant", null);
        assertNull(b2);
    }
}
