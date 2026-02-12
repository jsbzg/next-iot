package com.nextiot.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nextiot.common.entity.ThingDevice;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import com.nextiot.config.mapper.ThingDeviceMapper;
import com.nextiot.config.producer.ConfigEventProducer;
import com.nextiot.config.service.DeviceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备管理服务实现
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<ThingDeviceMapper, ThingDevice> implements DeviceService {

    @Resource
    private ConfigEventProducer configEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThingDevice createDevice(ThingDevice device) {
        // 保存到数据库
        device.setCreatedAt(System.currentTimeMillis());
        device.setUpdatedAt(System.currentTimeMillis());
        device.setOnline(true);
        save(device);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.DEVICE, ConfigOpType.ADD, device);

        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThingDevice updateDevice(ThingDevice device) {
        // 更新数据库
        device.setUpdatedAt(System.currentTimeMillis());
        updateById(device);

        // 发送配置变更事件
        configEventProducer.sendConfigChangeEvent(ConfigChangeType.DEVICE, ConfigOpType.UPDATE, device);

        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(String deviceCode) {
        // 查询设备
        ThingDevice device = getOne(new LambdaQueryWrapper<ThingDevice>()
                .eq(ThingDevice::getDeviceCode, deviceCode));

        if (device != null) {
            // 删除数据库记录
            removeById(device.getId());

            // 发送配置变更事件
            configEventProducer.sendConfigChangeEvent(ConfigChangeType.DEVICE, ConfigOpType.DELETE, device);
        }
    }

    @Override
    public List<ThingDevice> getAllDevices() {
        return list();
    }
}