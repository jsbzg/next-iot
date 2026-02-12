package com.nextiot.alarm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nextiot.common.entity.AlarmEvent;
import com.nextiot.common.entity.AlarmInstance;

/**
 * 告警服务接口
 * 负责业务抑制、生命周期管理、通知编排
 */
public interface AlarmService extends IService<AlarmInstance> {

    /**
     * 创建告警实例
     */
    AlarmInstance createAlarmInstance(AlarmEvent event);

    /**
     * 更新告警实例
     */
    AlarmInstance updateAlarmInstance(AlarmInstance instance, AlarmEvent event);

    /**
     * 发送告警通知（Demo 级别只打日志）
     */
    void sendNotification(AlarmInstance instance);

    /**
     * 人工确认告警
     */
    void ackAlarm(Long id, String user);

    /**
     * 恢复告警（当检测到数据恢复正常时调用）
     */
    void recoverAlarm(String ruleCode, String deviceCode);
}