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

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="https://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {

	static final int topics = 3;
	static final int split = 3;
	static final int brokers = 2; // Interestingly, it's fine for one broker to be unreachable initially - try setting this to 3 without adding another broker

	public static String brokerList() {
		return IntStream.range(0, brokers).mapToObj(i -> "kafka" + i + ":9091").collect(Collectors.joining(","));
	}

	public static void main(String[] args) throws Exception {

		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setParallelism(6);
		env.setStateBackend((StateBackend)new MemoryStateBackend(true));
		env.enableCheckpointing(10, CheckpointingMode.EXACTLY_ONCE);
		env.getCheckpointConfig().setMinPauseBetweenCheckpoints(3000);

		SingleOutputStreamOperator<Integer> sheep = input(IntStream.range(0, topics).mapToObj(i -> "sheep" + i).collect(Collectors.toList()), env);
		SingleOutputStreamOperator<Integer> goodnight = getIntegerSingleOutputStreamOperator(env);

		List<OutputTag<Integer>> splitTags = IntStream.range(0, split)
				.mapToObj(i -> new OutputTag<>("split" + i, TypeInformation.of(Integer.class)))
				.collect(Collectors.toList());
		SingleOutputStreamOperator<Void> sleepier = sheep.union(goodnight).keyBy(x -> x).process(new KeyedProcessFunction<Integer, Integer, Void>() {
			@Override
			public void processElement(Integer value, Context ctx, Collector<Void> out) throws Exception {
				ctx.output(splitTags.get((value / topics) % split), value + 1);
			}
		}).name("zzZzzZzz");
		for(int i = 0; i < split; i++) {
			List<OutputTag<Integer>> outputTags = IntStream.range(0, topics)
					.mapToObj(j -> new OutputTag<>("split" + j, TypeInformation.of(Integer.class)))
					.collect(Collectors.toList());
			SingleOutputStreamOperator<Void> asleep = sleepier.getSideOutput(splitTags.get(i)).keyBy(x -> x).process(new KeyedProcessFunction<Integer, Integer, Void>() {
				@Override
				public void processElement(Integer value, Context ctx, Collector<Void> out) throws Exception {
						ctx.output(outputTags.get(value % topics), value);
				}
			}).name("ZZzZZzZZ");
			for (int j = 0; j < topics; j++) {
				output("sheep" + j, asleep.getSideOutput(outputTags.get(j)));
			}
		}

		// execute program
		env.execute("ZZZZZ");
	}

	private static void output(String topic, DataStream<Integer> sleepier) {
		Properties producerSettings = new Properties();
		producerSettings.setProperty("bootstrap.servers", brokerList());
		sleepier.addSink(new FlinkKafkaProducer<>(
				topic,
				(KafkaSerializationSchema<Integer>) (element, timestamp) -> new ProducerRecord<>(topic, new byte[0], String.format("%d sheep", element).getBytes()),
				producerSettings,
				FlinkKafkaProducer.Semantic.AT_LEAST_ONCE
		)).name("Kafka loop out to " + topic);
	}

	private static SingleOutputStreamOperator<Integer> getIntegerSingleOutputStreamOperator(StreamExecutionEnvironment env) {
		return env.addSource(new SourceFunction<Integer>() {
				@Override
				public void run(SourceContext<Integer> ctx) {
					while (true) {
						try {
							Thread.sleep(10000);
						} catch (InterruptedException ignored) {
						}
						ctx.collect(0);
					}
				}

				@Override
				public void cancel() {

				}
			}).name("Initial sheep injector");
	}

	private static SingleOutputStreamOperator<Integer> input(List<String> topic, StreamExecutionEnvironment env) {
		Properties consumerSettings = new Properties();
		consumerSettings.setProperty("flink.partition-discovery.interval-millis", "60000"); // The actual interval seems to be of no importance...?
		consumerSettings.setProperty("bootstrap.servers", brokerList());
		consumerSettings.setProperty("group.id", "joebs");
		return env.addSource(new FlinkKafkaConsumer<>(
				topic,
					new KafkaDeserializationSchema<Integer>() {
						@Override
						public TypeInformation<Integer> getProducedType() {
							return TypeInformation.of(Integer.class);
						}

						@Override
						public boolean isEndOfStream(Integer integer) {
							return false;
						}

						@Override
						public Integer deserialize(ConsumerRecord<byte[], byte[]> consumerRecord) throws Exception {
							String data = new String(consumerRecord.value());
							if (data.endsWith(" sheep")) {
								return Integer.parseInt(data.substring(0, data.length() - 6));
							} else {
								return 0;
							}
						}
					},
					consumerSettings
			)).name("Kafka loop in from " + topic);
	}
}
