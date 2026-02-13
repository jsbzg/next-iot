
import json
import time
from kafka import KafkaProducer

BOOTSTRAP_SERVERS = 'localhost:9092'
CONFIG_TOPIC = 'config-topic'

def get_producer():
    try:
        producer = KafkaProducer(
            bootstrap_servers=BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        return producer
    except Exception as e:
        print(f"Failed to connect to Kafka: {e}")
        return None

def init_rules():
    producer = get_producer()
    if not producer:
        return

    print(f"Connected to Kafka. Push default rules to {CONFIG_TOPIC}...")

    # 1. Parse Rule (MQTT)
    # 对应 init.sql 中的默认规则，但必须通过 Kafka 发送给 Flink
    parse_rule_payload = {
        "gatewayType": "MQTT",
        "version": 2,
        "matchExpr": "deviceCode != nil",
        "parseScript": "let m = seq.map(); m.deviceCode = raw.deviceCode; m.ts = raw.ts; m.temperature = raw.temperature; m.humidity = raw.humidity; m.pressure = raw.pressure; return m;",
        "mappingScript": "let out = seq.map(); out.deviceCode = parsed.deviceCode; out.ts = parsed.ts; if(parsed.temperature!=nil){ out.propertyCode='temperature'; out.value=parsed.temperature; }elsif(parsed.humidity!=nil){ out.propertyCode='humidity'; out.value=parsed.humidity; }elsif(parsed.pressure!=nil){ out.propertyCode='pressure'; out.value=parsed.pressure; } return out;",
        "enabled": True
    }
    
    parse_event = {
        "type": "PARSE_RULE",
        "op": "UPDATE",
        "payload": parse_rule_payload
    }
    
    producer.send(CONFIG_TOPIC, parse_event)
    print(f"Sent ParseRule (MQTT/v1)")

    # 2. Alarm Rule (Example)
    alarm_rule_payload = {
        "ruleCode": "TEMP_HIGH_3",
        "deviceCode": "dev_001",
        "propertyCode": "temperature",
        "conditionExpr": "value > 80",
        "triggerType": "CONTINUOUS_N",
        "triggerN": 3,
        "suppressSeconds": 60,
        "level": 2,
        "description": "Temperature High (>80) 3 times",
        "enabled": True
    }

    alarm_event = {
        "type": "ALARM_RULE",
        "op": "UPDATE",
        "payload": alarm_rule_payload
    }

    producer.send(CONFIG_TOPIC, alarm_event)
    print(f"Sent AlarmRule (TEMP_HIGH_3)")

    # 3. Device Config (REQUIRED for validation)
    device_payload = {
        "deviceCode": "dev_001",
        "modelCode": "SENSOR_TPH",
        "gatewayType": "MQTT",
        "deviceName": "Test Device 001",
        "online": True,
        "lastSeenTs": int(time.time() * 1000)
    }
    
    device_event = {
        "type": "DEVICE",
        "op": "UPDATE",
        "payload": device_payload
    }

    producer.send(CONFIG_TOPIC, device_event)
    print(f"Sent Device Config (dev_001)")

    producer.flush()
    producer.close()
    print("Done. Rules initialized in Kafka.")

if __name__ == "__main__":
    init_rules()
