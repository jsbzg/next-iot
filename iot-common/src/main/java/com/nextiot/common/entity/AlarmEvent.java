package com.nextiot.common.entity;

import lombok.Data;
import com.nextiot.common.enums.AlarmLevel;

/**
 * 告警事件（Flink 输出到 alarm-event-topic，已检测 + 已流内抑制）
 */
@Data
public class AlarmEvent {
    /**
     * 告警规则编码
     */
    private String ruleCode;
    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 点位编码
     */
    private String propertyCode;
    /**
     * 当前数值
     */
    private Double value;
    /**
     * 当前数值（字符串格式）
     */
    private String strValue;
    /**
     * 告警级别
     */
    private AlarmLevel level;
    /**
     * 告警描述
     */
    private String description;
    /**
     * 触发时间戳
     */
    private Long ts;

    /**
     * 离线告警构造器
     */
    public static AlarmEvent createOfflineEvent(OfflineRule rule, String deviceCode, Long ts) {
        AlarmEvent event = new AlarmEvent();
        event.setRuleCode("OFFLINE_" + deviceCode);
        event.setDeviceCode(deviceCode);
        event.setValue(0.0);
        event.setLevel(AlarmLevel.WARNING);
        event.setDescription("设备离线超时：" + rule.getTimeoutSeconds() + "秒");
        event.setTs(ts);
        return event;
    }
}