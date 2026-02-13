package com.nextiot.alarm.consumer;

import com.alibaba.fastjson2.JSON;
import com.nextiot.alarm.entity.DeviceMetricData;
import com.nextiot.alarm.mapper.DeviceMetricDataMapper;
import com.nextiot.common.entity.MetricData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricDataConsumer {

    private final DeviceMetricDataMapper deviceMetricDataMapper;

    /**
     * 消费 Metric 数据并入库
     * 建议配置 batchListener 以提高吞吐量
     */
    @KafkaListener(topics = "${iot.metric-topic:metric-topic}", groupId = "alarm-service-metric-group")
    public void consume(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        
        log.info("收到 Metric 数据批量: {} 条", records.size());

        List<DeviceMetricData> entityList = records.stream()
                .map(record -> {
                    try {
                        String value = record.value();
                        MetricData metric = JSON.parseObject(value, MetricData.class);
                        if (metric == null) return null;

                        DeviceMetricData entity = new DeviceMetricData();
                        entity.setDeviceCode(metric.getDeviceCode());
                        entity.setPropertyCode(metric.getPropertyCode());
                        entity.setValue(metric.getValue());
                        entity.setStrValue(metric.getStrValue());
                        entity.setTs(metric.getTs());
                        entity.setCreatedAt(System.currentTimeMillis());
                        return entity;
                    } catch (Exception e) {
                        log.error("解析 MetricData 失败: {}", record.value(), e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (!entityList.isEmpty()) {
            // Mybatis-Plus 默认 insert 是单条的，为提高性能通常使用 IService.saveBatch
            // 这里为了简单演示直接循环插入，生产环境建议使用 Batch Insert
            for (DeviceMetricData data : entityList) {
                deviceMetricDataMapper.insert(data);
            }
            log.debug("成功入库 Metric 数据: {} 条", entityList.size());
        }
    }
}
