package com.nextiot.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nextiot.common.entity.ThingDevice;

import java.util.List;

/**
 * 设备管理服务接口
 */
public interface DeviceService extends IService<ThingDevice> {

    /**
     * 创建设备并发送配置变更事件
     */
    ThingDevice createDevice(ThingDevice device);

    /**
     * 更新设备并发送配置变更事件
     */
    ThingDevice updateDevice(ThingDevice device);

    /**
     * 删除设备并发送配置变更事件
     */
    void deleteDevice(String deviceCode);

    /**
     * 获取所有设备（用于 Flink 广播状态同步）
     */
    List<ThingDevice> getAllDevices();
}