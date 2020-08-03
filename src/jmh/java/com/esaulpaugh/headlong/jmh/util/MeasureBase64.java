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
package com.esaulpaugh.headlong.jmh.util;

import com.esaulpaugh.headlong.util.FastBase64;
import com.esaulpaugh.headlong.util.Strings;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Base64;
import java.util.Random;

@State(Scope.Thread)
public class MeasureBase64 {

    private static final byte[] SMALL = Base64.getUrlDecoder().decode("-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8");

    private static final byte[] LARGE = new byte[500_000];

    @Setup(Level.Trial)
    public void setUp() {
        new Random(System.currentTimeMillis() + System.nanoTime())
                .nextBytes(LARGE);
    }

//    @Benchmark
//    @Fork(value = 1, warmups = 1)
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 1)
//    @Measurement(iterations = 5)
//    public void javaUtilSmall() {
//        Base64.getUrlEncoder().encode(SMALL);
//    }
//
//    @Benchmark
//    @Fork(value = 1, warmups = 1)
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 1)
//    @Measurement(iterations = 5)
//    public void javaUtilLarge() {
//        Base64.getUrlEncoder().encode(LARGE);
//    }
//
//    @Benchmark
//    @Fork(value = 1, warmups = 1)
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 1)
//    @Measurement(iterations = 5)
//    public void migSmall() {
//        com.migcomponents.migbase64.Base64.encodeToBytes(SMALL, 0, SMALL.length, Strings.URL_SAFE_FLAGS);
//    }
//
//    @Benchmark
//    @Fork(value = 1, warmups = 1)
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 1)
//    @Measurement(iterations = 5)
//    public void migLarge() {
//        com.migcomponents.migbase64.Base64.encodeToBytes(LARGE, 0, LARGE.length, Strings.URL_SAFE_FLAGS);
//    }
//
//    @Benchmark
//    @Fork(value = 1, warmups = 1)
//    @BenchmarkMode(Mode.Throughput)
//    @Warmup(iterations = 1)
//    @Measurement(iterations = 5)
//    public void fastSmall() {
//        FastBase64.encodeToBytes(SMALL, 0, SMALL.length, Strings.URL_SAFE_FLAGS);
//    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 1)
    @Measurement(iterations = 5)
    public void fastLarge() {
        FastBase64.encodeToBytes(LARGE, 0, LARGE.length, Strings.URL_SAFE_FLAGS);
    }
}
