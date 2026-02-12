package com.nextiot.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nextiot.common.entity.ThingDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备实例 Mapper（设备白名单主数据）
 * 可替换点：当前使用 MySQL，后续可迁移 Redis / 配置中心缓存数据
 */
@Mapper
public interface ThingDeviceMapper extends BaseMapper<ThingDevice> {
}