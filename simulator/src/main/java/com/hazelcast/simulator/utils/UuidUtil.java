/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.simulator.utils;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Util class to generate type 4 (pseudo randomly generated) {@link UUID}.
 *
 * Creates {@link UUID} of different quality of randomness,
 * which are based on cryptographically weak {@link Random}
 * and strong {@link SecureRandom} pseudo random number generators.
 */
public final class UuidUtil {

    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);
    private static final long MOTHER_OF_MAGIC_NUMBERS = 181783497276652981L;

    private static final ThreadLocal<Random> THREAD_LOCAL_UNSECURE_RANDOM = new ThreadLocal<Random>() {
        @Override
        protected Random initialValue() {
            // Using the same way as the OpenJDK version just to
            // make sure this happens on every JDK implementation
            // since there are some out there that just use System.currentTimeMillis()
            return new Random(seedUniquifier() ^ System.nanoTime());
        }
    };

    private static final ThreadLocal<SecureRandom> THREAD_LOCAL_SECURE_RANDOM = new ThreadLocal<SecureRandom>() {
        @Override
        protected SecureRandom initialValue() {
            return new SecureRandom();
        }
    };

    private UuidUtil() {
    }

    /**
     * Static factory to retrieve a type 4 (pseudo randomly generated) UUID based string.
     *
     * The {@code UUID} string is generated using a cryptographically weak pseudo random number generator.
     *
     * @return A randomly generated {@code UUID} base string
     */
    public static String newUnsecureUuidString() {
        return newUnsecureUUID().toString();
    }

    /**
     * Static factory to retrieve a type 4 (pseudo randomly generated) UUID based string.
     *
     * The {@code UUID} string is generated using a cryptographically strong pseudo random number generator.
     *
     * @return A randomly generated {@code UUID} base string
     */
    public static String newSecureUuidString() {
        return newSecureUUID().toString();
    }

    /**
     * Static factory to retrieve a type 4 (pseudo randomly generated) UUID.
     *
     * The {@code UUID} is generated using a cryptographically weak pseudo random number generator.
     *
     * @return A randomly generated {@code UUID}
     */
    public static UUID newUnsecureUUID() {
        return getUUID(THREAD_LOCAL_UNSECURE_RANDOM.get());
    }

    /**
     * Static factory to retrieve a type 4 (pseudo randomly generated) UUID.
     *
     * The {@code UUID} is generated using a cryptographically strong pseudo random number generator.
     *
     * @return A randomly generated {@code UUID}
     */
    public static UUID newSecureUUID() {
        return getUUID(THREAD_LOCAL_SECURE_RANDOM.get());
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private static UUID getUUID(Random random) {
        byte[] data = new byte[16];
        random.nextBytes(data);

        // clear version
        data[6] &= 0x0f;
        // set to version 4
        data[6] |= 0x40;
        // clear variant
        data[8] &= 0x3f;
        // set to IETF variant
        data[8] |= 0x80;

        long mostSigBits = 0;
        long leastSigBits = 0;
        assert data.length == 16 : "data must be 16 bytes in length";
        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (data[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (data[i] & 0xff);
        }
        return new UUID(mostSigBits, leastSigBits);
    }

    private static long seedUniquifier() {
        // L'Ecuyer, "Tables of Linear Congruential Generators of Different Sizes and Good Lattice Structure", 1999
        while (true) {
            long current = SEED_UNIQUIFIER.get();
            long next = current * MOTHER_OF_MAGIC_NUMBERS;
            if (SEED_UNIQUIFIER.compareAndSet(current, next)) {
                return next;
            }
        }
    }
}
