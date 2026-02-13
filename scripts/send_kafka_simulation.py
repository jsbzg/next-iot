
import json
import time
import random
import sys
from kafka import KafkaProducer

# Kafka Configuration
BOOTSTRAP_SERVERS = 'localhost:9092'
RAW_TOPIC = 'raw-topic'
CONFIG_TOPIC = 'config-topic'

def get_producer():
    try:
        producer = KafkaProducer(
            bootstrap_servers=BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        print(f"Connected to Kafka at {BOOTSTRAP_SERVERS}")
        return producer
    except Exception as e:
        print(f"Failed to connect to Kafka: {e}")
        return None

def send_data(producer, device_code="dev_001", count=1):
    print(f"Sending {count} data messages for {device_code}...")
    for i in range(count):
        data = {
            "deviceCode": device_code,
            "ts": int(time.time() * 1000),
            "temperature": round(random.uniform(20.0, 30.0), 1),
            "humidity": round(random.uniform(40.0, 60.0), 1),
            "pressure": round(random.uniform(1000.0, 1020.0), 1)
        }
        
        # Simulate alarm condition occasionally
        if random.random() < 0.2:
            data["temperature"] = round(random.uniform(80.0, 100.0), 1)
            print(f"  [ALARM SIMULATION] High Temp: {data['temperature']}")

        producer.send(RAW_TOPIC, data)
        print(f"Sent to {RAW_TOPIC}: {data}")
        time.sleep(1)
    producer.flush()

def send_config_update(producer):
    print("Select Config Type:")
    print("1. Update ParseRule (Enable Aviator Script)")
    print("2. Add AlarmRule (Temp > 35)")
    print("3. Delete AlarmRule")
    choice = input("Enter choice: ")

    event = None
    
    if choice == '1':
        # Update ParseRule for MQTT to use strict script
        payload = {
            "gatewayType": "MQTT",
            "version": 2,
            "matchExpr": "string.contains(raw, 'deviceCode')",
            "parseScript": "let m = seq.map(); m.deviceCode = raw.deviceCode; m.ts = raw.ts; m.t = raw.temperature; return m;",
            "mappingScript": "let out = seq.map(); out.deviceCode = parsed.deviceCode; out.propertyCode = 'temperature'; out.value = parsed.t; out.ts = parsed.ts; return out;",
            "enabled": True
        }
        event = {
            "type": "PARSE_RULE",
            "op": "UPDATE",
            "payload": payload
        }
    elif choice == '2':
        # Add AlarmRule
        payload = {
            "ruleCode": "TEMP_HIGH_TEST",
            "deviceCode": "dev_001",
            "propertyCode": "temperature",
            "conditionExpr": "value > 35",
            "triggerType": "CONTINUOUS_N",
            "triggerN": 1,
            "suppressSeconds": 10,
            "level": 2,
            "description": "Test High Temp Alarm",
            "enabled": True
        }
        event = {
            "type": "ALARM_RULE",
            "op": "UPDATE", 
            "payload": payload
        }
    elif choice == '3':
        # Delete AlarmRule
        payload = {
            "ruleCode": "TEMP_HIGH_TEST"
        }
        event = {
            "type": "ALARM_RULE",
            "op": "DELETE",
            "payload": payload
        }

    if event:
        producer.send(CONFIG_TOPIC, event)
        print(f"Sent to {CONFIG_TOPIC}: {event}")
        producer.flush()

def main():
    producer = get_producer()
    if not producer:
        return

    while True:
        print("\n=== Verification Menu ===")
        print("1. Send Normal Data (dev_001)")
        print("2. Send High Temp Data (dev_001)")
        print("3. Send Config Update")
        print("4. Exit")
        
        choice = input("Select an option: ")
        
        if choice == '1':
            send_data(producer, "dev_001", count=1)
        elif choice == '2':
            # Send high temp
            data = {
                "deviceCode": "dev_001",
                "ts": int(time.time() * 1000),
                "temperature": 99.9,
                "humidity": 50.0
            }
            producer.send(RAW_TOPIC, data)
            print(f"Sent High Temp Data: {data}")
            producer.flush()
        elif choice == '3':
            send_config_update(producer)
        elif choice == '4':
            break
        
    producer.close()

if __name__ == "__main__":
    main()
