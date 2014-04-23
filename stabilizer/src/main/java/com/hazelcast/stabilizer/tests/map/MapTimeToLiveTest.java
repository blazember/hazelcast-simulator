/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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
package com.hazelcast.stabilizer.tests.map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.stabilizer.tests.AbstractTest;
import com.hazelcast.stabilizer.tests.TestRunner;

import java.util.UUID;

public class MapTimeToLiveTest extends AbstractTest {

    public int ttlSeconds = 1;
    public int workerCount = 3;
    public int putIntervalMillis = 10;
    public int waitAfterMillis = 2000;

    private IMap map;
    final private String mapName = "map:" + testId;

    @Override
    public void globalTearDown() throws Exception {
        map.destroy();
    }

    @Override
    public void globalVerify() throws Exception {
        Thread.sleep(ttlSeconds * 1000 + waitAfterMillis);
        int size = map.size();
        if (size > 0) {
            throw new RuntimeException("There are entries not evicted. Map size:" + size);
        }
    }

    @Override
    public void localSetup() throws Exception {
        HazelcastInstance targetInstance = getTargetInstance();

        map = targetInstance.getMap(mapName);
        for (int k = 0; k < workerCount; k++) {
            spawn(new Worker());
        }
    }

    class Worker implements Runnable {
        @Override
        public void run() {
            while (!stop) {
                try {
                    map.put(UUID.randomUUID(), UUID.randomUUID());
                    Thread.sleep(putIntervalMillis);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        MapTimeToLiveTest test = new MapTimeToLiveTest();
        new TestRunner().run(test, 50);
        System.exit(0);
    }
}
