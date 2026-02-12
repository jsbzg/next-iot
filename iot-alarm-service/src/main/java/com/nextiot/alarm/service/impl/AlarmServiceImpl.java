package com.nextiot.alarm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nextiot.alarm.mapper.AlarmInstanceMapper;
import com.nextiot.alarm.service.AlarmService;
import com.nextiot.common.entity.AlarmEvent;
import com.nextiot.common.entity.AlarmInstance;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 告警服务实现
 * 负责业务抑制、生命周期管理、通知编排
 * 注意：Flink 部分已完成流内抑制，这里只做业务层面抑制
 */
@Service
public class AlarmServiceImpl extends ServiceImpl<AlarmInstanceMapper, AlarmInstance> implements AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmServiceImpl.class);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmInstance createAlarmInstance(AlarmEvent event) {
        AlarmInstance instance = new AlarmInstance();
        instance.setRuleCode(event.getRuleCode());
        instance.setDeviceCode(event.getDeviceCode());
        instance.setPropertyCode(event.getPropertyCode());
        instance.setStatus("ACTIVE");
        instance.setLevel(event.getLevel().getLevel());
        instance.setDescription(event.getDescription());
        instance.setFirstTriggerTime(event.getTs());
        instance.setLastTriggerTime(event.getTs());
        instance.setCreatedAt(System.currentTimeMillis());
        instance.setUpdatedAt(System.currentTimeMillis());

        // 业务抑制判断（可扩展：维护窗口、静默策略等）
        if (!shouldSuppress(instance)) {
            save(instance);
            log.info("创建告警实例: ruleCode={}, deviceCode={}, level={}",
                    event.getRuleCode(), event.getDeviceCode(), event.getLevel());
        } else {
            log.info("告警被业务抑制: ruleCode={}, deviceCode={}",
                    event.getRuleCode(), event.getDeviceCode());
        }

        return instance;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmInstance updateAlarmInstance(AlarmInstance instance, AlarmEvent event) {
        // 更新最后触发时间
        instance.setLastTriggerTime(event.getTs());
        instance.setUpdatedAt(System.currentTimeMillis());

        // 如果已确认，重新激活
        if ("ACKED".equals(instance.getStatus()) || "RECOVERED".equals(instance.getStatus())) {
            instance.setStatus("ACTIVE");
            instance.setLevel(event.getLevel().getLevel());
            instance.setDescription(event.getDescription());
        }

        // 业务抑制判断
        if (!shouldSuppress(instance) && "ACTIVE".equals(instance.getStatus())) {
            updateById(instance);
            log.info("更新告警实例: id={}, lastTriggerTime={}", instance.getId(), event.getTs());
        }

        return instance;
    }

    @Override
    public void sendNotification(AlarmInstance instance) {
        // Demo 级别只打日志
        // 生产环境可扩展：短信、邮件、Webhook、钉钉、企业微信等
        log.info("【告警通知】规则：{}, 设备：{}, 级别：{}, 描述：{}, 状态：{}",
                instance.getRuleCode(), instance.getDeviceCode(),
                instance.getLevel(), instance.getDescription(), instance.getStatus());

        // TODO: 可扩展通知编排逻辑
        // - 短信通知
        // - 邮件通知
        // - Webhook 推送
        // - 钉钉/企业微信机器人
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ackAlarm(Long id, String user) {
        AlarmInstance instance = getById(id);
        if (instance != null && "ACTIVE".equals(instance.getStatus())) {
            instance.setStatus("ACKED");
            instance.setAckTime(System.currentTimeMillis());
            instance.setAckUser(user);
            instance.setUpdatedAt(System.currentTimeMillis());
            updateById(instance);
            log.info("告警已确认: id={}, user={}", id, user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverAlarm(String ruleCode, String deviceCode) {
        // 查找活跃告警
        AlarmInstance instance = lambdaQuery()
                .eq(AlarmInstance::getRuleCode, ruleCode)
                .eq(AlarmInstance::getDeviceCode, deviceCode)
                .eq(AlarmInstance::getStatus, "ACTIVE")
                .one();

        if (instance != null) {
            instance.setStatus("RECOVERED");
            instance.setRecoveredTime(System.currentTimeMillis());
            instance.setUpdatedAt(System.currentTimeMillis());
            updateById(instance);
            log.info("告警已恢复: ruleCode={}, deviceCode={}", ruleCode, deviceCode);
        }
    }

    /**
     * 业务抑制判断
     * 可扩展维护窗口、静默策略等业务逻辑
     */
    private boolean shouldSuppress(AlarmInstance instance) {
        // TODO: 实现业务抑制逻辑
        // 1. 维护窗口抑制
        // 2. 静默策略抑制
        // 3. 人工 ACK 后的抑制
        return false;
    }
}