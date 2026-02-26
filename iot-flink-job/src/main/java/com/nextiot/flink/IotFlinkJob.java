package com.nextiot.flink;

import com.alibaba.fastjson2.JSON;
import com.nextiot.common.entity.AlarmEvent;
import com.nextiot.common.entity.ConfigChangeEvent;
import com.nextiot.common.entity.IntermediateRawMessage;
import com.nextiot.common.entity.MetricData;
import com.nextiot.flink.function.IotDataProcessFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
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
 *
 * 从「一条网关报文 = 一个设备一条指标」➡️ 升级为「一条网关报文 = 多设备 × 多属性」。
 *
 * 你给的样例：
 *
 * [
 *   { "deviceCode": 123, "Ia": 2, "Ib": 3, "Ua": 2, "Uab": 120, "ts": 1672211233 },
 *   { "deviceCode": 467, "Ia": 12, "Ib": 23, "Ua": 2, "Uab": 120, "ts": 1672211233 }
 * ]
 *
 *
 * 语义是：
 *
 * 一条 MQTT / Kafka 消息里，包含 N 个设备，每个设备 M 个属性
 *
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
        // 保持原始消息为 String，所有格式判断、字段提取由 AviatorScript 完成
        KafkaSource<String> rawSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(rawTopic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // 步骤1：将原始消息包装为 IntermediateRawMessage
        DataStream<IntermediateRawMessage> rawStream = env
                .fromSource(rawSource, WatermarkStrategy.noWatermarks(), "Raw Source")
                .map(value -> {
                    // 提取 gatewayType（优先从消息中，否则根据特征判断）
                    String gatewayType = extractGatewayType(value);

                    IntermediateRawMessage wrapper = new IntermediateRawMessage();
                    wrapper.setRawMessage(value);
                    wrapper.setGatewayType(gatewayType);
                    wrapper.setReceiveTime(System.currentTimeMillis());
                    return wrapper;
                })
                .returns(TypeInformation.of(IntermediateRawMessage.class));

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
        // 使用 Fallback Key 策略: gatewayType + rawMessage.hashCode()
        SingleOutputStreamOperator<Tuple4<MetricData, AlarmEvent, AlarmEvent, String>> processedStream = rawStream
                .keyBy(msg -> {
                    String gatewayType = msg.getGatewayType() != null ? msg.getGatewayType() : "DEFAULT";
                    // 保证 hash 为正数，避免分区分配问题
                    int hash = msg.getRawMessage().hashCode() & 0x7FFFFFFF;
                    return gatewayType + "_" + hash;
                })
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

    /**
     * 提取 gatewayType
     * 策略1：尝试 JSON 解析，提取 gatewayType 字段
     * 策略2：根据消息特征判断格式类型
     *
     * @param rawMessage 原始消息字符串
     * @return gatewayType
     */
    private static String extractGatewayType(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "UNKNOWN";
        }

        // 策略1：尝试 JSON 解析，提取 gatewayType 字段
        try {
            Map<String, Object> json = JSON.parseObject(rawMessage, Map.class);
            Object gatewayType = json.get("gatewayType");
            if (gatewayType != null) {
                return String.valueOf(gatewayType);
            }
        } catch (Exception ignored) {
            // 不是 JSON 格式，继续策略2
        }

        // 策略2：根据消息特征判断
        String trimmed = rawMessage.trim();

        // JSON 格式（对象或数组）
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "MQTT"; // JSON 格式默认 MQTT
        }

        // 管道分隔符格式
        if (trimmed.contains("|")) {
            return "PIPE_DELIMITED";
        }

        // CSV 逗号分隔符格式
        if (trimmed.contains(",")) {
            return "CSV";
        }

        // Modbus Hex 格式（以 $ 开头或包含字母数字组合）
        if (trimmed.startsWith("$") || (trimmed.length() > 4 && trimmed.matches("[0-9A-Fa-f]+"))) {
            return "MODBUS_HEX";
        }

        // 固定长度格式（无特殊分隔符，可能是固定格式）
        if (trimmed.length() > 20 && !trimmed.contains(" ") && !trimmed.contains(",") && !trimmed.contains("|")) {
            return "FIXED_WIDTH";
        }

        return "UNKNOWN";
    }
}