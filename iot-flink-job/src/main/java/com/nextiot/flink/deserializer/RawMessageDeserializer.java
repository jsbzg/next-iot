package com.nextiot.flink.deserializer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nextiot.common.entity.MetricData;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 原始报文反序列化器
 * 将原始 JSON 报文转换为 MetricData 对象
 * 可替换点：后续可扩展支持 PB/JSON/XML 等多种格式
 */
public class RawMessageDeserializer implements KafkaDeserializationSchema<Map<String, Object>> {

    @Override
    public Map<String, Object> deserialize(ConsumerRecord<byte[], byte[]> record) {
        try {
            String json = new String(record.value(), "UTF-8");
            JSONObject obj = JSON.parseObject(json);
            // 转换为 Map 供后续 Aviator 使用
            return JSON.parseObject(json, Map.class);
        } catch (Exception e) {
            // 解析失败返回空
            return new HashMap<>();
        }
    }

    @Override
    public boolean isEndOfStream(Map<String, Object> nextElement) {
        return false;
    }

    @Override
    public TypeInformation<Map<String, Object>> getProducedType() {
        return TypeInformation.of(Map.class);
    }
}