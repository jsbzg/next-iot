package com.nextiot.alarm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nextiot.common.entity.AlarmInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警实例 Mapper
 * 可替换点：当前使用 MySQL，后续应迁移 Redis / ES
 */
@Mapper
public interface AlarmInstanceMapper extends BaseMapper<AlarmInstance> {
}