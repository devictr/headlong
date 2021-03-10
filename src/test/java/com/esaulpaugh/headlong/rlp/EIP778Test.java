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
package com.esaulpaugh.headlong.rlp;

import com.esaulpaugh.headlong.TestUtils;
import com.esaulpaugh.headlong.util.FastHex;
import com.esaulpaugh.headlong.util.Strings;
import org.junit.jupiter.api.Test;

import java.security.SignatureException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.esaulpaugh.headlong.TestUtils.assertThrown;
import static com.esaulpaugh.headlong.rlp.KeyValuePair.ID;
import static com.esaulpaugh.headlong.rlp.KeyValuePair.IP;
import static com.esaulpaugh.headlong.rlp.KeyValuePair.PAIR_COMPARATOR;
import static com.esaulpaugh.headlong.rlp.KeyValuePair.SECP256K1;
import static com.esaulpaugh.headlong.rlp.KeyValuePair.UDP;
import static com.esaulpaugh.headlong.util.Strings.EMPTY_BYTE_ARRAY;
import static com.esaulpaugh.headlong.util.Strings.HEX;
import static com.esaulpaugh.headlong.util.Strings.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class EIP778Test {

    static final byte[] SIG = FastHex.decode(
            "7098ad865b00a582051940cb9cf36836572411a47278783077011599ed5cd16b"
          + "76f2635f4e234738f30813a89eb9137e3e3df5266e3a1f11df72ecf1145ccb9c"
    );

    private static final Record.Signer SIGNER = new Record.Signer() {

        @Override
        public int signatureLength() {
            return SIG.length;
        }

        @Override
        public byte[] sign(byte[] content) {
            return SIG;
        }
    };

    private static final String ENR_STRING = "enr:-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8";

    private static final Record.Verifier VERIFIER = (s,c) -> {
        if(!Arrays.equals(s, SIG)) throw new SignatureException();
    };

    private static final String RECORD_HEX;

    static {
        try {
            RECORD_HEX = Strings.encode(Record.parse(ENR_STRING, VERIFIER).getRLP().encoding());
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Record VECTOR;

    static {
        try {
            VECTOR = Record.parse(ENR_STRING, VERIFIER);
        } catch (SignatureException se) {
            throw new RuntimeException(se);
        }
    }

    private static final byte[] MAX_LEN_LIST = new byte[] {
            (byte) 0xf9, (byte) 1, (byte) 41,
            (byte) 55,
            (byte) 0xca, (byte) 0xc9, (byte) 0x80, 0x00, (byte) 0x81, (byte) 0xFF, (byte) 0x81, (byte) 0x90, (byte) 0x81, (byte) 0xb6, (byte) '\u230A',
            (byte) 0xb8, 56, 0x09,(byte)0x80,-1,0,0,0,0,0,0,0,36,74,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, -3, -2, 0, 0,
            (byte) 0xf8, 0x38, 0,0,0,0,0,0,0,0,0,0,36,74,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0, 36, 74, 0, 0,
            (byte) 0x84, 'c', 'a', 't', 's',
            (byte) 0x84, 'd', 'o', 'g', 's',
            (byte) 0xca, (byte) 0x84, 92, '\r', '\n', '\f', (byte) 0x84, '\u0009', 'o', 'g', 's',
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,

            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0
    };

    @Test
    public void testParseErrs() throws Throwable {
        assertThrown(IllegalArgumentException.class, "unconsumed trailing bytes", () -> Record.parse(ENR_STRING + "A", VERIFIER));
        assertEquals(300, MAX_LEN_LIST.length);
        assertThrown(SignatureException.class, () -> Record.decode(MAX_LEN_LIST, VERIFIER));
        byte[] maxLenPlusOne = Arrays.copyOf(MAX_LEN_LIST, MAX_LEN_LIST.length + 1);
        maxLenPlusOne[2]++; // increment len in RLP prefix
        assertThrown(IllegalArgumentException.class, "record length exceeds maximum: 301 > 300", () -> Record.decode(maxLenPlusOne, VERIFIER));
    }

    @Test
    public void testErrs() throws Throwable {
        final long seq = -TestUtils.seededRandom().nextInt(Integer.MAX_VALUE);
        assertThrown(
                IllegalArgumentException.class,
                "negative seq",
                () -> new Record(new Record.Signer() {
                    @Override
                    public int signatureLength() {
                        return 0;
                    }

                    @Override
                    public byte[] sign(byte[] content) {
                        return null;
                    }
                }, seq)
        );
        assertThrown(
                RuntimeException.class,
                "signer specifies negative signature length",
                () -> new Record(new Record.Signer() {
                    @Override
                    public int signatureLength() {
                        return -1;
                    }

                    @Override
                    public byte[] sign(byte[] content) {
                        return null;
                    }
                }, 0x07)
        );
        assertThrown(
                NullPointerException.class,
                () -> new Record(new Record.Signer() {
                    @Override
                    public int signatureLength() {
                        return 0;
                    }

                    @Override
                    public byte[] sign(byte[] content) {
                        return null;
                    }
                }, 0L)
        );
    }

    @Test
    public void testSort() {
        Random r = TestUtils.seededRandom();
        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < 50; j++) {
                String a = generateASCIIString(j, r);
                String b = generateASCIIString(j, r);
                if(!a.equals(b)) {
                    int str = a.compareTo(b) < 0 ? 0 : 1;
                    KeyValuePair pairA = new KeyValuePair(a, EMPTY_BYTE_ARRAY);
                    KeyValuePair pairB = new KeyValuePair(b, EMPTY_BYTE_ARRAY);
                    int pair = pairA.compareTo(pairB) < 0 ? 0 : 1;
                    assertEquals(str, pair, pairA + " " + pairB);
                }
            }
        }
    }

    private static String generateASCIIString(final int len, Random r) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < len; i++) {
            sb.append((char) (r.nextInt(95) + 32));
        }
        return sb.toString();
    }

    @Test
    public void testEip778() throws SignatureException {
        final long seq = 1L;
        final List<KeyValuePair> pairs = Arrays.asList(
                new KeyValuePair(IP, "7f000001", HEX),
                new KeyValuePair(UDP, "765f", HEX),
                new KeyValuePair(ID, "v4", UTF_8),
                new KeyValuePair(SECP256K1, "03ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd3138", HEX)
        );
        final KeyValuePair[] empty = new KeyValuePair[0];
        final KeyValuePair[] array = pairs.toArray(empty);

        final Record record = new Record(SIGNER, seq, pairs);

        assertEquals(RECORD_HEX, record.getRLP().encodingString(HEX));

        assertEquals(VECTOR.getSignature(), record.getSignature());
        assertEquals(VECTOR.getContent(), record.getContent());
        assertEquals(VECTOR.getSeq(), record.getSeq());
        assertEquals(VECTOR.getRLP(), record.getRLP());
        assertEquals(VECTOR.toString(), record.toString());
        assertEquals(VECTOR, record);

        RLPList content = record.getContent();
        System.out.println("verified = " + content);
        final Iterator<RLPItem> contentIter = content.iterator(RLPDecoder.RLP_STRICT);

        assertEquals(seq, record.getSeq());
        assertEquals(seq, contentIter.next().asLong());

        Arrays.sort(array);

        List<KeyValuePair> pairList = record.getPairs();

        assertArrayEquals(array, record.getPairs().toArray(empty));

        Map<String, byte[]> map = record.map();
        assertArrayEquals(Strings.decode("765f"), record.map().get(UDP));
        assertArrayEquals(Strings.decode("v4", UTF_8), map.get(ID));
        assertArrayEquals(Strings.decode("03ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd3138"), map.get(SECP256K1));

        assertEquals(seq, record.visit((k, v) -> {}));

        final Iterator<KeyValuePair> listIter = pairList.iterator();
        final Iterator<Map.Entry<String, byte[]>> mapIter = map.entrySet().iterator();
        int i = 0;
        while (contentIter.hasNext() || listIter.hasNext() || mapIter.hasNext()) {
            final KeyValuePair expected = array[i];
            testEqual(expected, new KeyValuePair(contentIter.next(), contentIter.next()));
            testEqual(expected, listIter.next());
            Map.Entry<String, byte[]> e = mapIter.next();
            testEqual(expected, new KeyValuePair(e.getKey(), e.getValue()));
            testEqual(expected, expected.withValue(expected.value().asBytes()));
            i++;
        }
        assertEquals(ENR_STRING, record.toString());

        assertEquals(record, Record.parse(record.toString(), VERIFIER));
    }

    private static void testEqual(KeyValuePair a, KeyValuePair b) {
        assertNotSame(a, b);
        assertEquals(a, b);
    }

    @Test
    public void testZeroLenSig() {
        Record record = new Record(new Record.Signer() {
                    @Override
                    public int signatureLength() {
                        return 0;
                    }

                    @Override
                    public byte[] sign(byte[] content) {
                        return new byte[0];
                    }
                },
                1L,
                new KeyValuePair(IP, "7f000001", HEX),
                new KeyValuePair(UDP, "765f", HEX),
                new KeyValuePair(ID, "v4", UTF_8),
                new KeyValuePair(SECP256K1, "03ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd3138", HEX)
        );
        System.out.println(record.getSignature());
        for(RLPItem it : record.getContent()) {
            System.out.println(it);
        }
    }

    @Test
    public void testIncorrectSignatureLength() throws Throwable {
        assertThrown(RuntimeException.class,
                "unexpected signature length: 32 != 64",
                        () -> new Record(new Record.Signer() {
                            @Override
                            public int signatureLength() {
                                return 64;
                            }

                            @Override
                            public byte[] sign(byte[] content) {
                                return new byte[32];
                            }
                        },
                        90L,
                        new KeyValuePair(IP, "7f000001", HEX),
                        new KeyValuePair(UDP, "765f", HEX),
                        new KeyValuePair(ID, "v4", UTF_8),
                        new KeyValuePair(SECP256K1, "03ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd3138", HEX)));
    }

    @Test
    public void testDuplicateKeys() throws Throwable {
        byte[] keyBytes = new byte[0];
        final List<KeyValuePair> pairs = Arrays.asList(new KeyValuePair(keyBytes, new byte[0]), new KeyValuePair(keyBytes, new byte[1]));
        assertThrown(IllegalArgumentException.class, "duplicate key", () -> pairs.sort(PAIR_COMPARATOR));

        final List<KeyValuePair> pairs2 = Arrays.asList(new KeyValuePair(new byte[] { 2 }, new byte[0]), new KeyValuePair(new byte[] { 2 }, new byte[1]));
        assertThrown(IllegalArgumentException.class, "duplicate key", () -> pairs2.sort(PAIR_COMPARATOR));
    }

    @Test
    public void nineLengths() {
        Set<Integer> recordLengths = new HashSet<>();
        for (long p = 0, seq = 0; p <= 64; p += 8, seq = (long) Math.pow(2.0, p)) {
            long temp = seq - 2;
            int i = 0;
            do {
                if(temp >= 0) {
                    Record r = new Record(SIGNER, temp);
                    int len = r.getRLP().encodingLength();
                    System.out.println(temp + " -> " + len);
                    recordLengths.add(len);
                }
                temp++;
            } while (++i < 4);
        }
        assertEquals(9, recordLengths.size());
    }

    @Test
    public void testDuplicateKey() throws Throwable {
        long seq = 3L;

        final List<KeyValuePair> pairs = Arrays.asList(
                new KeyValuePair(IP, "7f000001", HEX),
                new KeyValuePair(UDP, "765f", HEX),
                new KeyValuePair(ID, "v4", UTF_8),
                new KeyValuePair(SECP256K1, "03ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd3138", HEX),
                new KeyValuePair(UDP, "0000", HEX)
        );

        for (KeyValuePair p : pairs) {
            System.out.println(p);
        }

        assertThrown(IllegalArgumentException.class, "duplicate key: " + UDP, () -> new Record(SIGNER, seq, pairs));
    }
}
