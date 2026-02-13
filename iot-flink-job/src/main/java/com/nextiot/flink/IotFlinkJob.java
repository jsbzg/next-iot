package com.nextiot.flink;

import com.alibaba.fastjson2.JSON;
import com.nextiot.common.entity.AlarmEvent;
import com.nextiot.common.entity.ConfigChangeEvent;
import com.nextiot.common.entity.MetricData;
import com.nextiot.flink.function.IotDataProcessFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * IoT 数据中台 Flink 处理作业
 */
public class IotFlinkJob {

    private static final Logger log = LoggerFactory.getLogger(IotFlinkJob.class);

    public static void main(String[] args) throws Exception {
        // 1. 设置执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 解析命令行参数
        // 例如: --bootstrap.servers kafka:29092 --parallelism 2
        ParameterTool params = ParameterTool.fromArgs(args);
        
        // 获取配置，提供默认值
        String bootstrapServers = params.get("bootstrap.servers", "kafka:29092");
        int parallelism = params.getInt("parallelism", 1);
        String rawTopic = params.get("raw.topic", "raw-topic");
        String configTopic = params.get("config.topic", "config-topic");
        String alarmTopic = params.get("alarm.topic", "alarm-event-topic");
        String groupId = params.get("group.id", "iot-flink-group");
        //解析后数据输出
        String metricTopic = params.get("metric.topic", "metric-topic");

        env.setParallelism(parallelism);
        
        // 将参数注册为全局配置，方便在 Function 中获取 (如果需要)
        env.getConfig().setGlobalJobParameters(params);

        // 2. 配置 Source
        // 2.1 原始数据流 (raw-topic)
        KafkaSource<String> rawSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(rawTopic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<Map<String, Object>> rawStream = env
                .fromSource(rawSource, WatermarkStrategy.noWatermarks(), "Raw Source")
                .map((MapFunction<String, Map<String, Object>>) value -> {
                    log.info("[SOURCE-DEBUG] >>> 收到原始 Kafka 消息: {}", value);
                    return (Map<String, Object>) JSON.parseObject(value, Map.class);
                })
                .returns(new TypeHint<Map<String, Object>>() {
                }.getTypeInfo());

        // 2.2 配置广播流 (config-topic)
        KafkaSource<String> configSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(configTopic)
                .setGroupId(groupId + "-config")
                .setStartingOffsets(OffsetsInitializer.latest()) // 配置变更需要最新的读取
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<ConfigChangeEvent> configStream = env
                .fromSource(configSource, WatermarkStrategy.noWatermarks(), "Config Source")
                .map(json -> JSON.parseObject(json, ConfigChangeEvent.class));

        // 3. 构建 Broadcast Stream
        BroadcastStream<ConfigChangeEvent> broadcastStream = configStream.broadcast(
                IotDataProcessFunction.DEVICE_STATE_DESC,
                IotDataProcessFunction.PARSE_RULE_STATE_DESC,
                IotDataProcessFunction.ALARM_RULE_STATE_DESC,
                IotDataProcessFunction.OFFLINE_RULE_STATE_DESC
        );

        // 4. 连接流并处理
        // out: Tuple4<MetricData, AlarmEvent, AlarmEvent, String>
        SingleOutputStreamOperator<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> processedStream = rawStream
                .keyBy(map -> String.valueOf(map.get("deviceCode")))
                .connect(broadcastStream)
                .process(new IotDataProcessFunction());

        // 5. 分流与 Sink
        // 5.1 侧输出脏数据
        processedStream.getSideOutput(IotDataProcessFunction.DIRTY_DATA_OUTPUT)
                .print("DIRTY");

        // 5.2 业务指标数据 Sink (Metric Topic)
        DataStream<String> metricStream = processedStream
                .flatMap((FlatMapFunction<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>, String>) (value, out) -> {
                    if (value.f0 != null) {
                        out.collect(JSON.toJSONString(value.f0));
                    }
                })
                .returns(Types.STRING);

        KafkaSink<String> metricSink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(metricTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .build();
        metricStream.sinkTo(metricSink);
        metricStream.print("METRIC");

        // 5.3 告警与离线事件 Sink (Alarm Topic)
        DataStream<String> alarmStream = processedStream
                .flatMap((FlatMapFunction<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>, String>) (value, out) -> {
                    if (value.f1 != null) {
                        out.collect(JSON.toJSONString(value.f1));
                    }
                    if (value.f2 != null) {
                        out.collect(JSON.toJSONString(value.f2));
                    }
                })
                .returns(Types.STRING);

        KafkaSink<String> alarmSink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(alarmTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .build();

        alarmStream.sinkTo(alarmSink);
        alarmStream.print("ALARM");

        env.execute("IoT Data Platform Job");
    }
}