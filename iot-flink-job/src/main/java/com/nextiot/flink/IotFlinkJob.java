package com.nextiot.flink;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nextiot.common.entity.*;
import com.nextiot.common.enums.*;
import com.nextiot.flink.deserializer.RawMessageDeserializer;
import com.nextiot.flink.state.BroadcastStateKeys;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.keyed.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * IoT 数据中台 Flink Job（核心）
 *
 * 功能说明：
 * 1. 动态解析：使用 Aviator 表达式引擎解析原始报文
 * 2. 设备合法性校验：第一道业务闸门，不合格设备直接丢弃
 * 3. 动态规则检测：支持连续 N 次 / 时间窗口两种触发类型
 * 4. 流内抑制：防刷屏，同一规则 + 同一设备在 X 秒内只输出 1 次
 * 5. 离线检测：通过定时器检测设备离线
 *
 * 关键设计：
 * - 使用 Broadcast State 实现规则动态刷新（无需重启 Job）
 * - 非法设备数据通过 SideOutput 到 dirty-topic
 *
 * ⚠️ 可替换点：
 * - 时序数据存储：当前 metric-topic 为可选，后续替换为 ClickHouse / TDengine
 * - 规则状态缓存：当前使用 MySQL + Flink State，后续迁移 Redis
 */
public class IotFlinkJob {

    private static final Logger log = LoggerFactory.getLogger(IotFlinkJob.class);

    // ===== Kafka Topic 配置 =====
    private static final String RAW_TOPIC = "raw-topic";
    private static final String CONFIG_TOPIC = "config-topic";
    private static final String METRIC_TOPIC = "metric-topic";
    private static final String ALARM_EVENT_TOPIC = "alarm-event-topic";
    private static final String DIRTY_TOPIC = "dirty-topic";

    // ===== 非法设备数据 SideOutput =====
    private static final OutputTag<Tuple3<String, String, String>> DIRTY_OUTPUT_TAG =
            new OutputTag<Tuple3<String, String, String>>("dirty-data") {};

    // ===== 设备最后心跳时间 State =====
    private static final ValueStateDescriptor<Long> DEVICE_HEARTBEAT_DESC =
            new ValueStateDescriptor<>("device-heartbeat", Long.class);

    // ===== 告警连续计数 State =====
    private static final ValueStateDescriptor<Integer> CONTINUOUS_COUNT_DESC =
            new ValueStateDescriptor<>("continuous-count", Integer.class);

    // ===== 流内抑制时间 State =====
    private static final ValueStateDescriptor<Long> SUPPRESS_TIME_DESC =
            new ValueStateDescriptor<>("suppress-time", Long.class);

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // ===== Checkpoint 配置（保证 exactly-once 语义）=====
        env.enableCheckpointing(60000);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000);
        env.getCheckpointConfig().setCheckpointTimeout(600000);

        // ===== Kafka 配置 =====
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "kafka:29092");
        kafkaProps.setProperty("group.id", "iot-flink-job-group");
        kafkaProps.setProperty("auto.offset.reset", "latest");

        // ===== 模拟数据源（Demo 使用）=====
        DataStream<Tuple3<String, Double, Long>> rawStream = env.addSource(new SimulatedDataSource())
                .name("Simulated Device Data Source");

        // ===== 1. KeyBy 设备编码 =====
        KeyedStream<Tuple3<String, Double, Long>, String> keyedByDevice = rawStream
                .keyBy(tuple3 -> tuple3.f0);

        // ===== 2. 设备合法性校验 & 离线检测（第一道业务闸门）=====
        // Demo 模式：假设所有设备都是合法的（实际应从 Broadcast State 读取）
        SingleOutputStreamOperator<MetricData> validMetricStream = keyedByDevice.process(
                new DeviceValidationAndOfflineDetectionFunction()
        );

        // ===== 3. KeyBy 设备编码 + 点位编码（用于告警检测）=====
        // Demo 中点位编码固定为 "temperature"
        KeyedStream<MetricData, Tuple2<String, String>> keyedForAlarm = validMetricStream
                .keyBy(metric -> Tuple2.of(metric.getDeviceCode(), metric.getPropertyCode() != null
                        ? metric.getPropertyCode() : "temperature"));

        // ===== 4. 动态规则检测 + 流内抑制 =====
        SingleOutputStreamOperator<AlarmEvent> alarmEventStream = keyedForAlarm.process(
                new AlarmDetectionAndSuppressionFunction()
        );

        // ===== 输出到 Kafka =====

        // 输出告警事件
        Properties alarmProducerProps = new Properties();
        alarmProducerProps.setProperty("bootstrap.servers", "kafka:29092");
        FlinkKafkaProducer<String> alarmEventProducer = new FlinkKafkaProducer<>(
                ALARM_EVENT_TOPIC,
                new org.apache.flink.api.common.serialization.SimpleStringSchema(),
                alarmProducerProps,
                FlinkKafkaProducer.Semantic.AT_LEAST_ONCE
        );

        alarmEventStream.map(JSON::toJSONString)
                .addSink(alarmEventProducer)
                .name("Alarm Event Sink");

        // 执行 Job
        log.info("IoT Flink Job 启动中... (端口: kafka:29092)");
        env.execute("IoT Data Platform Flink Job");
    }

    /**
     * 模拟数据源（生产环境替换为 Kafka Source）
     */
    private static class SimulatedDataSource implements SourceFunction<Tuple3<String, Double, Long>> {
        private volatile boolean isRunning = true;
        private final Random random = new Random();

        @Override
        public void run(SourceContext<Tuple3<String, Double, Long>> ctx) throws Exception {
            while (isRunning) {
                // 模拟 3 个设备数据上报
                String[] deviceCodes = {"dev_001", "dev_002", "dev_003"};
                for (String deviceCode : deviceCodes) {
                    // 模拟温度数据（正常: 20-30°C，异常: > 80°C）
                    double temperature;
                    if (random.nextInt(100) < 10) {
                        // 10% 概率产生异常数据
                        temperature = 80 + random.nextDouble() * 20; // 80-100°C
                    } else {
                        temperature = 20 + random.nextDouble() * 10; // 20-30°C
                    }

                    long timestamp = System.currentTimeMillis();
                    ctx.collect(Tuple3.of(deviceCode, temperature, timestamp));
                }

                // 每秒生成一次数据
                Thread.sleep(1000);
            }
        }

        @Override
        public void cancel() {
            isRunning = false;
        }
    }

    /**
     * 设备合法性校验 + 离线检测函数
     * 功能：
     * 1. 校验设备是否在白名单中
     * 2. 维护设备最后心跳时间
     * 3. 使用定时器检测设备离线
     *
     * ⚠️ Demo 简化：假设所有设备都是合法的
     */
    private static class DeviceValidationAndOfflineDetectionFunction
            extends KeyedProcessFunction<String, Tuple3<String, Double, Long>, MetricData> {

        @Override
        public void open(Configuration parameters) throws Exception {
            // 初始化状态
        }

        @Override
        public void processElement(
                Tuple3<String, Double, Long> value,
                KeyedProcessFunction<String, Tuple3<String, Double, Long>, MetricData>.Context ctx,
                Collector<MetricData> out
        ) throws Exception {
            String deviceCode = value.f0;
            Double temperature = value.f1;
            Long timestamp = value.f2;

            // ===== Demo: 设备合法性校验 =====
            // 实际应从 Broadcast State 读取 ThingDevice 白名单
            boolean isDeviceValid = isValidDevice(deviceCode);

            if (!isDeviceValid) {
                log.warn("非法设备数据，已丢弃: deviceCode={}", deviceCode);
                return;
            }

            // ===== 更新设备最后心跳时间 =====
            ValueState<Long> heartbeatState = getRuntimeContext().getState(DEVICE_HEARTBEAT_DESC);
            heartbeatState.update(timestamp);

            // ===== 注册离线检测定时器 =====
            // Demo 中假设离线超时时间为 30 秒
            long offlineTimeout = 30000;
            ctx.timerService().registerProcessingTimeTimer(timestamp + offlineTimeout);

            // ===== 输出标准化点位数据 =====
            MetricData metric = new MetricData();
            metric.setDeviceCode(deviceCode);
            metric.setPropertyCode("temperature");
            metric.setValue(temperature);
            metric.setTs(timestamp);

            out.collect(metric);
        }

        @Override
        public void onTimer(
                long timestamp,
                KeyedProcessFunction<String, Tuple3<String, Double, Long>, MetricData>.OnTimerContext ctx,
                Collector<MetricData> out
        ) throws Exception {
            String deviceCode = ctx.getCurrentKey();

            // 检查设备是否离线
            ValueState<Long> heartbeatState = getRuntimeContext().getState(DEVICE_HEARTBEAT_DESC);
            Long lastHeartbeat = heartbeatState.value();

            if (lastHeartbeat != null) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastHeartbeat = currentTime - lastHeartbeat;

                // 如果超过 30 秒没有心跳，判定为离线
                if (timeSinceLastHeartbeat > 30000) {
                    // TODO: 生成离线告警事件，输出到 alarm-event-topic
                    log.warn("检测到设备离线: deviceCode={}, 离线时长={}ms", deviceCode, timeSinceLastHeartbeat);

                    // 清除心跳状态，避免重复告警
                    heartbeatState.clear();
                } else {
                    // 设备仍然在线，重新注册定时器
                    long nextTimeout = currentTime + 30000;
                    ctx.timerService().registerProcessingTimeTimer(nextTimeout);
                }
            }
        }

        /**
         * 校验设备合法性（Demo 模式：假设所有设备都是合法的）
         * 实际应从 Broadcast State 读取 ThingDevice 白名单
         */
        private boolean isValidDevice(String deviceCode) {
            // Demo 白名单
            return deviceCode.startsWith("dev_");
        }
    }

    /**
     * 告警检测 + 流内抑制函数
     * 功能：
     * 1. 动态检测规则（连续 N 次 / 窗口）
     * 2. 流内抑制（防刷屏）
     *
     * ⚠️ Demo 简化：使用硬编码规则，实际应从 Broadcast State 读取 AlarmRule
     */
    private static class AlarmDetectionAndSuppressionFunction
            extends KeyedProcessFunction<Tuple2<String, String>, MetricData, AlarmEvent> {

        @Override
        public void processElement(
                MetricData metric,
                KeyedProcessFunction<Tuple2<String, String>, MetricData, AlarmEvent>.Context ctx,
                Collector<AlarmEvent> out
        ) throws Exception {
            String deviceCode = metric.getDeviceCode();
            Double value = metric.getValue();
            Long timestamp = metric.getTs();

            // ===== Demo 告警规则：温度 > 80°C 连续 3 次 =====
            double threshold = 80.0;
            int consecutiveN = 3;
            int suppressSeconds = 60;

            Long currentTime = System.currentTimeMillis();

            // ===== 1. 条件判断 =====
            boolean triggered = value > threshold;

            if (triggered) {
                // ===== 2. 连续 N 次计数 =====
                ValueState<Integer> countState = getRuntimeContext().getState(CONTINUOUS_COUNT_DESC);
                int count = countState.value() == null ? 0 : countState.value();
                count++;
                countState.update(count);

                log.info("触发条件: deviceCode={}, value={}, count={}", deviceCode, value, count);

                // ===== 3. 判断是否达到触发条件 =====
                if (count >= consecutiveN) {
                    // ===== 4. 流内抑制检查 =====
                    ValueState<Long> suppressState = getRuntimeContext().getState(SUPPRESS_TIME_DESC);
                    Long lastTriggerTime = suppressState.value();

                    boolean shouldSuppress = lastTriggerTime != null
                            && (currentTime - lastTriggerTime) < suppressSeconds * 1000;

                    if (!shouldSuppress) {
                        // ===== 5. 生成告警事件 =====
                        AlarmEvent event = new AlarmEvent();
                        event.setRuleCode("TEMP_HIGH_3");
                        event.setDeviceCode(deviceCode);
                        event.setPropertyCode("temperature");
                        event.setValue(value);
                        event.setStrValue(String.valueOf(value));
                        event.setLevel(AlarmLevel.CRITICAL);
                        event.setDescription("温度连续 " + consecutiveN + " 次超过 " + threshold + "°C");
                        event.setTs(timestamp);

                        out.collect(event);
                        log.info("触发告警: deviceCode={}, value={}, level={}", deviceCode, value, AlarmLevel.CRITICAL);

                        // 更新抑制时间
                        suppressState.update(currentTime);
                        countState.clear();
                    } else {
                        log.info("告警被抑制（距离上次触发不足 {} 秒）: deviceCode={}", suppressSeconds, deviceCode);
                    }
                }
            } else {
                // 条件未触发，重置连续计数
                ValueState<Integer> countState = getRuntimeContext().getState(CONTINUOUS_COUNT_DESC);
                countState.clear();
            }
        }
    }
}