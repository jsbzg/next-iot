package com.nextiot.flink.function;

import com.alibaba.fastjson2.JSON;
import com.nextiot.common.entity.AlarmEvent;
import com.nextiot.common.entity.AlarmRule;
import com.nextiot.common.entity.MetricData;
import com.nextiot.common.entity.OfflineRule;
import com.nextiot.common.enums.TriggerType;
import com.nextiot.common.util.AviatorUtil;
import com.nextiot.flink.state.BroadcastStateKeys;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警检测处理函数
 * 职责：
 * 1. 动态规则检测（连续N次 / 窗口）
 * 2. 流内抑制（防止告警刷屏）
 */
public class AlarmDetectionProcessFunction extends KeyedProcessFunction<
        Tuple2<String, String>, MetricData, AlarmEvent> {

    private static final Logger log = LoggerFactory.getLogger(AlarmDetectionProcessFunction.class);

    // 连续计数状态：Key = ruleCode, Value = 连续命中次数
    private transient ValueState<Integer> continuousCountState;

    // 流内抑制状态：Key = ruleCode, Value = 上次触发时间
    private transient ValueState<Long> suppressState;

    @Override
    public void open(Configuration parameters) throws Exception {
        ValueStateDescriptor<Integer> continuousCountDesc = new ValueStateDescriptor<>(
                "continuous-count", Integer.class, 0);
        continuousCountState = getRuntimeContext().getState(continuousCountDesc);

        ValueStateDescriptor<Long> suppressDesc = new ValueStateDescriptor<>(
                "suppress-time", Long.class, 0L);
        suppressState = getRuntimeContext().getState(suppressDesc);
    }

    @Override
    public void processElement(
            MetricData metric,
            KeyedProcessFunction<Tuple2<String, String>, MetricData, AlarmEvent>.Context ctx,
            Collector<AlarmEvent> out
    ) throws Exception {
        String deviceCode = metric.getDeviceCode();
        String propertyCode = metric.getPropertyCode();
        Double value = metric.getValue();
        Long ts = metric.getTs();

        // 获取当前时间（ProcessingTime）
        long currentTime = System.currentTimeMillis();

        // TODO: 这里需要访问的 Broadcast State
        // 由于 KeyedProcessFunction 无法直接访问 Broadcast State
        // 实际实现应该在 BroadcastProcessFunction 中完成检测逻辑
        // 这里的简化实现仅做演示

        log.debug("处理点位数据: deviceCode={}, propertyCode={}, value={}", deviceCode, propertyCode, value);
    }
}