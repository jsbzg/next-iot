package com.nextiot.config.controller;

import com.nextiot.common.dto.Result;
import com.nextiot.common.entity.ThingDevice;
import com.nextiot.config.service.DeviceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理控制器
 */
@RestController
@RequestMapping("/api/device")
@CrossOrigin(origins = "*")
public class DeviceController {

    @Resource
    private DeviceService deviceService;

    /**
     * 查询所有设备
     */
    @GetMapping("/list")
    public Result<List<ThingDevice>> list() {
        List<ThingDevice> devices = deviceService.list();
        return Result.success(devices);
    }

    /**
     * 查询单个设备
     */
    @GetMapping("/{deviceCode}")
    public Result<ThingDevice> getByDeviceCode(@PathVariable String deviceCode) {
        ThingDevice device = deviceService.lambdaQuery()
                .eq(ThingDevice::getDeviceCode, deviceCode)
                .one();
        if (device == null) {
            return Result.fail("设备不存在");
        }
        return Result.success(device);
    }

    /**
     * 创建设备
     */
    @PostMapping("/create")
    public Result<ThingDevice> create(@RequestBody ThingDevice device) {
        try {
            ThingDevice created = deviceService.createDevice(device);
            return Result.success(created);
        } catch (Exception e) {
            return Result.fail("创建设备失败：" + e.getMessage());
        }
    }

    /**
     * 更新设备
     */
    @PostMapping("/update")
    public Result<ThingDevice> update(@RequestBody ThingDevice device) {
        try {
            ThingDevice updated = deviceService.updateDevice(device);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.fail("更新设备失败：" + e.getMessage());
        }
    }

    /**
     * 删除设备
     */
    @DeleteMapping("/{deviceCode}")
    public Result<Void> delete(@PathVariable String deviceCode) {
        try {
            deviceService.deleteDevice(deviceCode);
            return Result.success();
        } catch (Exception e) {
            return Result.fail("删除设备失败：" + e.getMessage());
        }
    }

    /**
     * 获取所有设备（用于 Flink 广播状态同步）
     */
    @GetMapping("/all")
    public Result<List<ThingDevice>> getAllDevices() {
        List<ThingDevice> devices = deviceService.getAllDevices();
        return Result.success(devices);
    }
}