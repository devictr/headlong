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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static com.esaulpaugh.headlong.util.JsonUtils.getArray;
import static com.esaulpaugh.headlong.util.JsonUtils.getBoolean;
import static com.esaulpaugh.headlong.util.JsonUtils.getString;
import static com.esaulpaugh.headlong.util.JsonUtils.parseArray;
import static com.esaulpaugh.headlong.util.JsonUtils.parseObject;

/** For parsing JSON representations of {@link ABIObject}s according to the ABI specification. */
public final class ABIJSON {

    private static final String NAME = "name";
    private static final String TYPE = "type";
    static final String FUNCTION = "function";
    static final String RECEIVE = "receive";
    static final String FALLBACK = "fallback";
    static final String CONSTRUCTOR = "constructor";
    private static final String INPUTS = "inputs";
    private static final String OUTPUTS = "outputs";
    private static final String TUPLE = "tuple";
    private static final String COMPONENTS = "components";
    private static final String EVENT = "event";
    private static final String ANONYMOUS = "anonymous";
    private static final String INDEXED = "indexed";
    private static final String STATE_MUTABILITY = "stateMutability";
    private static final String PURE = "pure";
    private static final String VIEW = "view";
    static final String PAYABLE = "payable";
//    private static final String NONPAYABLE = "nonpayable";// to mark as nonpayable, do not specify any stateMutability
    private static final String CONSTANT = "constant"; // deprecated

    public static ABIObject parseABIObject(String objectJson) {
        return parseABIObject(parseObject(objectJson));
    }

    public static ABIObject parseABIObject(JsonObject object) {
        return EVENT.equals(getString(object, TYPE))
                ? parseEvent(object)
                : parseFunction(object, Function.newDefaultDigest());
    }

    public static List<Function> parseFunctions(String arrayJson) {
        return parseObjects(arrayJson, true, false, Function.class);
    }

    public static List<Event> parseEvents(String arrayJson) {
        return parseObjects(arrayJson, false, true, Event.class);
    }

    private static <T extends ABIObject> List<T> parseObjects(final String jsonArrStr,
                                                             final boolean functions,
                                                             final boolean events,
                                                             final Class<T> classOfT) {
        final MessageDigest defaultDigest = functions ? Function.newDefaultDigest() : null;

        final List<T> abiObjects = new ArrayList<>();
        for (JsonElement e : parseArray(jsonArrStr)) {
            if (e.isJsonObject()) {
                JsonObject jsonObj = (JsonObject) e;
                switch (getString(jsonObj, TYPE)) {
                case FUNCTION:
                case RECEIVE:
                case FALLBACK:
                case CONSTRUCTOR:
                    if (functions) {
                        abiObjects.add(classOfT.cast(parseFunction(jsonObj, defaultDigest)));
                    }
                    break;
                case EVENT:
                    if (events) {
                        abiObjects.add(classOfT.cast(parseEvent(jsonObj)));
                    }
                    break;
                default: /* skip */
                }
            }
        }
        return abiObjects;
    }

    public static Function parseFunction(JsonObject function, MessageDigest messageDigest) {
        return new Function(
                parseFunctionType(function),
                getString(function, NAME),
                parseTypes(getArray(function, INPUTS)),
                parseTypes(getArray(function, OUTPUTS)),
                getString(function, STATE_MUTABILITY),
                messageDigest
        );
    }
// ---------------------------------------------------------------------------------------------------------------------
    private static Function.Type parseFunctionType(JsonObject function) {
        final String type = getString(function, TYPE);
        if(type != null) {
            switch (type) {
            case FUNCTION: return Function.Type.FUNCTION;
            case FALLBACK: return Function.Type.FALLBACK;
            case CONSTRUCTOR: return Function.Type.CONSTRUCTOR;
            case RECEIVE: return Function.Type.RECEIVE;
            default: throw new IllegalArgumentException("unexpected type: \"" + type + "\"");
            }
        }
        return Function.Type.FUNCTION;
    }

    private static TupleType parseTypes(JsonArray array) {
        if (array != null) {
            final ABIType<?>[] elementsArray = new ABIType[array.size()];
            for (int i = 0; i < elementsArray.length; i++) {
                elementsArray[i] = parseType(array.get(i).getAsJsonObject());
            }
            return TupleType.wrap(elementsArray);
        }
        return TupleType.EMPTY;
    }

    static Event parseEvent(JsonObject event) {
        final String type = getString(event, TYPE);
        if (EVENT.equals(type)) {
            final JsonArray inputs = getArray(event, INPUTS);
            if (inputs != null) {
                final int inputsLen = inputs.size();
                final ABIType<?>[] inputsArray = new ABIType[inputsLen];
                final boolean[] indexed = new boolean[inputsLen];
                for (int i = 0; i < inputsLen; i++) {
                    JsonObject inputObj = inputs.get(i).getAsJsonObject();
                    inputsArray[i] = parseType(inputObj);
                    indexed[i] = getBoolean(inputObj, INDEXED);
                }
                return new Event(
                        getString(event, NAME),
                        TupleType.wrap(inputsArray),
                        indexed,
                        getBoolean(event, ANONYMOUS, false)
                );
            }
            throw new IllegalArgumentException("array \"" + INPUTS + "\" null or not found");
        }
        throw new IllegalArgumentException("unexpected type: " + (type == null ? null : "\"" + type + "\""));
    }

    private static ABIType<?> parseType(JsonObject object) {
        final String typeStr = getString(object, TYPE);
        if(typeStr.startsWith(TUPLE)) {
            final JsonArray components = getArray(object, COMPONENTS);
            final ABIType<?>[] componentsArray = new ABIType[components.size()];
            int i = 0;
            for (JsonElement c : components) {
                componentsArray[i++] = parseType(c.getAsJsonObject()); // parse component names as well as types
            }
            return TypeFactory.createFromBase(
                    TupleType.wrap(componentsArray),
                    typeStr.substring(TUPLE.length()), // suffix e.g. "[4][]"
                    getString(object, NAME)
            );
        }
        return TypeFactory.create(typeStr, getString(object, NAME));
    }
// ---------------------------------------------------------------------------------------------------------------------
    static JsonObject buildFunctionJson(Function f) {
        final JsonObject json = new JsonObject();
        final Function.Type type = f.getType();
        json.add(TYPE, new JsonPrimitive(type.toString()));
        if(type != Function.Type.FALLBACK) {
            addIfValueNotNull(NAME, f.getName(), json);
            if(type != Function.Type.RECEIVE) {
                json.add(INPUTS, buildJsonArray(f.getParamTypes(), null));
                if(type != Function.Type.CONSTRUCTOR) {
                    json.add(OUTPUTS, buildJsonArray(f.getOutputTypes(), null));
                }
            }
        }
        final String stateMutability = f.getStateMutability();
        addIfValueNotNull(STATE_MUTABILITY, stateMutability, json);
        json.add(CONSTANT, new JsonPrimitive(VIEW.equals(stateMutability) || PURE.equals(stateMutability)));
        return json;
    }

    static JsonObject buildEventJson(Event e) {
        final JsonObject json = new JsonObject();
        json.add(TYPE, new JsonPrimitive(EVENT));
        addIfValueNotNull(NAME, e.getName(), json);
        json.add(INPUTS, buildJsonArray(e.getParams(), e.getIndexManifest()));
        return json;
    }

    private static JsonArray buildJsonArray(TupleType tupleType, boolean[] indexedManifest) {
        final JsonArray array = new JsonArray();
        for (int i = 0; i < tupleType.elementTypes.length; i++) {
            final ABIType<?> e = tupleType.elementTypes[i];
            final JsonObject arrayElement = new JsonObject();
            final String name = e.getName();
            arrayElement.add(NAME, name == null ? null : new JsonPrimitive(name));
            final String type = e.canonicalType;
            if(type.startsWith("(")) { // tuple
                arrayElement.add(TYPE, new JsonPrimitive(type.replace(type.substring(0, type.lastIndexOf(')') + 1), TUPLE)));
                ABIType<?> base = e;
                while (base instanceof ArrayType) {
                    base = ((ArrayType<? extends ABIType<?>, ?>) base).elementType;
                }
                arrayElement.add(COMPONENTS, buildJsonArray((TupleType) base, null));
            } else {
                arrayElement.add(TYPE, new JsonPrimitive(type));
            }
            array.add(arrayElement);
            if(indexedManifest != null) {
                arrayElement.add(INDEXED, new JsonPrimitive(indexedManifest[i]));
            }
        }
        return array;
    }

    private static void addIfValueNotNull(String key, String value, JsonObject object) {
        if(value != null) {
            object.add(key, new JsonPrimitive(value));
        }
    }
}
