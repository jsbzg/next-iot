package com.nextiot.alarm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("device_metric_data")
public class DeviceMetricData {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceCode;
    private String propertyCode;
    private Double value;
    private String strValue;
    private Long ts;
    private Long createdAt;
}
