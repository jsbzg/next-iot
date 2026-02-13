package com.nextiot.alarm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nextiot.alarm.entity.DeviceMetricData;
import com.nextiot.alarm.service.DeviceMetricDataService;
import com.nextiot.common.dto.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 设备指标数据控制器
 */
@RestController
@RequestMapping("/api/metric-data")
@CrossOrigin(origins = "*")
public class MetricDataController {

    @Resource
    private DeviceMetricDataService deviceMetricDataService;

    /**
     * 分页查询指标数据
     */
    @GetMapping("/page")
    public Result<Page<DeviceMetricData>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(name = "deviceCode", required = false) String deviceCode,
            @RequestParam(name = "propertyCode", required = false) String propertyCode) {

        Page<DeviceMetricData> page = new Page<>(current, size);
        LambdaQueryWrapper<DeviceMetricData> wrapper = new LambdaQueryWrapper<>();

        if (deviceCode != null && !deviceCode.isEmpty()) {
            wrapper.eq(DeviceMetricData::getDeviceCode, deviceCode);
        }
        if (propertyCode != null && !propertyCode.isEmpty()) {
            wrapper.eq(DeviceMetricData::getPropertyCode, propertyCode);
        }

        wrapper.orderByDesc(DeviceMetricData::getTs);

        return Result.success(deviceMetricDataService.page(page, wrapper));
    }
}
