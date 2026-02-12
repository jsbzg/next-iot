package com.nextiot.alarm.consumer;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nextiot.alarm.mapper.AlarmInstanceMapper;
import com.nextiot.alarm.service.AlarmService;
import com.nextiot.common.dto.Result;
import jakarta.annotation.Resource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 告警事件消费者
 * 接收 Flink 已检测 + 已流内抑制的告警事件
 * 负责业务抑制、生命周期管理、通知编排
 */
@Component
public class AlarmEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlarmEventConsumer.class);

    @Resource
    private AlarmInstanceMapper alarmInstanceMapper;

    @Resource
    private AlarmService alarmService;

    @KafkaListener(
            topics = "${spring.kafka.consumer.topics}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeAlarmEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            // 解析告警事件
            String json = record.value();
            com.nextiot.common.entity.AlarmEvent event = JSON.parseObject(json, com.nextiot.common.entity.AlarmEvent.class);

            log.info("接收到告警事件: ruleCode={}, deviceCode={}, value={}",
                    event.getRuleCode(), event.getDeviceCode(), event.getValue());

            // 查找或创建告警实例
            AlarmInstance instance = alarmInstanceMapper.selectOne(
                    new LambdaQueryWrapper<AlarmInstance>()
                            .eq(AlarmInstance::getRuleCode, event.getRuleCode())
                            .eq(AlarmInstance::getDeviceCode, event.getDeviceCode())
                            .in(AlarmInstance::getStatus, "ACTIVE", "ACKED")
                            .orderByDesc(AlarmInstance::getLastTriggerTime)
                            .last("LIMIT 1")
            );

            if (instance == null) {
                // 创建新的告警实例
                instance = alarmService.createAlarmInstance(event);
            } else {
                // 更新现有告警实例
                instance = alarmService.updateAlarmInstance(instance, event);
            }

            // 触发通知（Demo 级别只打日志）
            alarmService.sendNotification(instance);

            // 手动提交 offset
            ack.acknowledge();

            log.info("告警事件处理完成: instanceId={}, status={}", instance.getId(), instance.getStatus());

        } catch (Exception e) {
            log.error("处理告警事件失败: {}", record.value(), e);
            // 异常情况下不提交 offset，让消息重试
        }
    }
}