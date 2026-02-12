package com.nextiot.config.producer;

import com.alibaba.fastjson2.JSON;
import com.nextiot.common.entity.ConfigChangeEvent;
import com.nextiot.common.enums.ConfigChangeType;
import com.nextiot.common.enums.ConfigOpType;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 生产者 - 发送配置变更事件
 * 所有配置变更都会发送到 config-topic，供 Flink 通过 Broadcast State 实时更新
 */
@Component
public class ConfigEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ConfigEventProducer.class);

    @Value("${spring.kafka.producer.topic:config-topic}")
    private String configTopic;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 发送配置变更事件
     * @param type 变更类型：DEVICE / PARSE_RULE / ALARM_RULE / OFFLINE_RULE
     * @param op 操作类型：ADD / UPDATE / DELETE
     * @param payload 变更内容
     */
    public void sendConfigChangeEvent(ConfigChangeType type, ConfigOpType op, Object payload) {
        try {
            ConfigChangeEvent event = new ConfigChangeEvent();
            event.setType(type);
            event.setOp(op);
            event.setTimestamp(System.currentTimeMillis());
            event.setPayload(toMap(payload));

            String json = JSON.toJSONString(event);

            // 发送到 Kafka config-topic
            kafkaTemplate.send(configTopic, type.getCode(), json)
                    .addCallback(
                            result -> log.info("配置变更事件发送成功: type={}, op={}", type, op),
                            failure -> log.error("配置变更事件发送失败: type={}, op={}, error={}",
                                    type, op, failure.getMessage())
                    );
        } catch (Exception e) {
            log.error("发送配置变更事件异常: type={}, op={}, error={}", type, op, e.getMessage(), e);
            throw new RuntimeException("发送配置变更事件失败", e);
        }
    }

    /**
     * 将对象转换为 Map
     */
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        return JSON.parseObject(JSON.toJSONString(obj));
    }
}