/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fujitsu.labs.michaelis;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import java.util.Properties;

public class StreamingJob {

  public static void main(String[] args) throws Exception{

    String topicName = "sheep";
    float freq = 333f;
    int threads = 1000;
    long start = System.currentTimeMillis();

    for (int i = 0; i < threads; i ++) {
      int t = i;
      new Thread(() -> {

        Properties props = new Properties();
        props.put("bootstrap.servers", "kafka1:9091,kafka2:9091");
        props.put("acks", "all");
        props.put("retries", 2147483647);
        props.put("batch.size", 16384);
        props.put("linger.ms", 0);
        props.put("buffer.memory", 33554432);
        props.put("client.id", "zzzz-" + t);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> producer = new KafkaProducer<>(props);

        long sheep = 0;
        long nextMeta = 0;
        while (true) {
          long now = System.currentTimeMillis();
          long sleep = start + (long)(threads / freq * 1000 * sheep + t / freq * 1000) - now;
          if (sleep > 0) {
            try {
              Thread.sleep(sleep);
            } catch (InterruptedException e) {
            }
          }
          sheep++;
          producer.send(new ProducerRecord<>(topicName, Long.toString(sheep), t + "-" + sleep));
          if (nextMeta < now) {
            nextMeta += 60_000;
            producer.partitionsFor(topicName);
          }
        }
      }).start();
    }
  }
}