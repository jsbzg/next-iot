package com.nextiot.common.constant;

/**
 * Kafka Topic 常量类
 * 统一管理所有 Topic 名称
 */
public class KafkaTopicConstants {

    /**
     * 原始网关报文 Topic
     */
    public static final String RAW_TOPIC = "raw-topic";

    /**
     * 标准化点位数据 Topic
     */
    public static final String METRIC_TOPIC = "metric-topic";

    /**
     * 告警事件 Topic（已检测 + 已流内抑制）
     */
    public static final String ALARM_EVENT_TOPIC = "alarm-event-topic";

    /**
     * 配置变更事件 Topic
     * 用于广播给 Flink 进行动态配置刷新
     */
    public static final String CONFIG_TOPIC = "config-topic";

    /**
     * 非法设备/解析失败数据 Topic（SideOutput）
     */
    public static final String DIRTY_TOPIC = "dirty-topic";

    private KafkaTopicConstants() {
        // 私有构造函数，防止实例化
    }
}
