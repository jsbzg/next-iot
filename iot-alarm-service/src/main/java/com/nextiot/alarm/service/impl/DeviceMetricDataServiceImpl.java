package com.nextiot.alarm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nextiot.alarm.entity.DeviceMetricData;
import com.nextiot.alarm.mapper.DeviceMetricDataMapper;
import com.nextiot.alarm.service.DeviceMetricDataService;
import org.springframework.stereotype.Service;

/**
 * 设备指标数据服务实现
 */
@Service
public class DeviceMetricDataServiceImpl extends ServiceImpl<DeviceMetricDataMapper, DeviceMetricData> implements DeviceMetricDataService {
}
