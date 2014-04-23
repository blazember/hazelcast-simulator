package com.hazelcast.stabilizer.tests.regression;

import com.hazelcast.stabilizer.tests.AbstractTest;

//https://gist.github.com/mgrossmann/9620178
public class Test2051 extends AbstractTest {


    //    private ExecutorService pool;
//    private volatile boolean stopRunning;
//    private IQueue<Data> activityQueue;
//    private Map<String, Data> activeDataMap;
//    private Queue<Data> preProcessingQueue;
//    private Queue<Data> postProcessingQueue;
//    private Queue<Data> sendQueue;
//    private String instanceName;
//
//    @Override
//    public void localSetup() throws Exception {
//        super.localSetup();
//
//        HazelcastInstance instance = getTargetInstance();
//
//        activityQueue = instance.getQueue("activityQueue-" + nodeName);
//        instanceName = instance.getConfig().getInstanceName();
//
//        activeDataMap = instance.getMap("activeDataObjects");
//        preProcessingQueue = instance.getQueue("preProcessingQueue");
//        postProcessingQueue = instance.getQueue("postProcessingQueue");
//        sendQueue = instance.getQueue("sendQueue");
//        pool = Executors.newFixedThreadPool(1000);
//    }
//
//    // a closure that will be submitted to the thread pool as a producer
//    class Producer implements Runnable {
//
//        public void run() {
//            while (!stopRunning) {
//                try {
//                    String key = instanceName + " - " + UUID.randomUUID();
//                    Data data = new Data(key:key, state:
//                    "CREATED BY producer ${Thread.currentThread().id} on ${instance.config.instanceName}")
//
//                    activityQueue.add(key);
//
//                    activeDataMap.put(key, data);
//
//                    Data updatedData = activeDataMap.get(key);
//                    updatedData.state = "QUEUED BY producer ${Thread.currentThread().id} on ${instance.config.instanceName}"
//                    activeDataMap.put(key, updatedData);
//
//                    preProcessingQueue.add(updatedData);
//                    activityQueue.remove(key);
//
//                    sleep 50
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }
//
//
//    // the data object
//    static class Data implements Serializable {
//
//        private static final long serialVersionUID = 1L;
//
//        String key;
//        String state;
//
//        byte[] data = new byte[2048];
//
//        @Override
//        public String toString() {
//            return format("%s -> %s", key, state);
//        }
//    }

}
