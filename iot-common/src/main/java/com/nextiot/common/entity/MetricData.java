package com.nextiot.common.entity;

import lombok.Data;

/**
 * 标准化点位数据（Flink 输出到 metric-topic）
 */
@Data
public class MetricData {
    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 点位编码
     */
    private String propertyCode;
    /**
     * 数据值
     */
    private Double value;
    /**
     * 数据值（字符串格式）
     */
    private String strValue;
    /**
     * 时间戳
     */
    private Long ts;
}