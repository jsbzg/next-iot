"""
Kafka 消息模拟脚本 - 支持多种报文格式测试
支持格式：JSON、CSV、管道符分隔、固定长度等
"""

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
    """创建 Kafka Producer"""
    try:
        producer = KafkaProducer(
            bootstrap_servers=BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v).encode('utf-8') if isinstance(v, dict) else v.encode('utf-8')
        )
        print(f"Connected to Kafka at {BOOTSTRAP_SERVERS}")
        return producer
    except Exception as e:
        print(f"Failed to connect to Kafka: {e}")
        return None


def send_json_data(producer, device_code="dev_001", high_temp=False):
    """发送 JSON 格式数据（带 gatewayType）"""
    ts = int(time.time() * 1000)
    temperature = round(random.uniform(80.0, 100.0), 1) if high_temp else round(random.uniform(20.0, 30.0), 1)

    data = {
        "gatewayType": "MQTT",
        "deviceCode": device_code,
        "ts": ts,
        "temperature": temperature,
        "humidity": round(random.uniform(40.0, 60.0), 1),
        "pressure": round(random.uniform(1000.0, 1020.0), 1)
    }

    # 发送为 String 格式（Flink 接收原始字符串）
    message_str = json.dumps(data)
    producer.send(RAW_TOPIC, message_str)
    print(f"[JSON] Sent to {RAW_TOPIC}: {message_str}")


def send_csv_data(producer, device_code="dev_004", high_temp=False):
    """发送 CSV 格式数据"""
    ts = int(time.time() * 1000) // 1000  # 秒级时间戳
    temperature = round(random.uniform(80.0, 100.0), 1) if high_temp else round(random.uniform(20.0, 30.0), 1)
    humidity = round(random.uniform(40.0, 60.0), 1)
    pressure = round(random.uniform(1000.0, 1020.0), 1)

    # CSV 格式: deviceCode,ts,temperature,humidity,pressure
    message_str = f"{device_code},{ts},{temperature},{humidity},{pressure}"
    producer.send(RAW_TOPIC, message_str)
    print(f"[CSV] Sent to {RAW_TOPIC}: {message_str}")


def send_pipe_data(producer, device_code="dev_005", high_temp=False):
    """发送管道符分隔格式数据"""
    ts = int(time.time() * 1000) // 1000  # 秒级时间戳
    temperature = round(random.uniform(80.0, 100.0), 1) if high_temp else round(random.uniform(20.0, 30.0), 1)

    # 管道符格式: deviceCode|ts|temperature
    message_str = f"{device_code}|{ts}|{temperature}"
    producer.send(RAW_TOPIC, message_str)
    print(f"[PIPE] Sent to {RAW_TOPIC}: {message_str}")


def send_fixed_width_data(producer, device_code="dev_003", high_temp=False):
    """发送固定长度格式数据"""
    ts = int(time.time() * 1000) // 1000  # 秒级时间戳
    temperature = round(random.uniform(80.0, 100.0), 1) if high_temp else round(random.uniform(20.0, 30.0), 1)

    # 固定长度格式: deviceCode(7) + ts(13) + temperature(6)
    # deviceCode 右补空格到7位，ts 左补0到13位，temperature 固定4位 + 1位小数
    padded_device = device_code.ljust(7)
    padded_ts = str(ts).zfill(13)
    padded_temp = f"{temperature:4.1f}"

    message_str = f"{padded_device}{padded_ts}{padded_temp}"
    producer.send(RAW_TOPIC, message_str)
    print(f"[FIXED] Sent to {RAW_TOPIC}: {message_str}")


def send_json_array_data(producer):
    """发送 JSON 数组格式（多设备）"""
    ts = int(time.time() * 1000)

    data = [
        {
            "gatewayType": "MQTT",
            "deviceCode": "dev_001",
            "ts": ts,
            "temperature": round(random.uniform(20.0, 30.0), 1)
        },
        {
            "gatewayType": "MQTT",
            "deviceCode": "dev_002",
            "ts": ts,
            "temperature": round(random.uniform(20.0, 30.0), 1)
        }
    ]

    message_str = json.dumps(data)
    producer.send(RAW_TOPIC, message_str)
    print(f"[JSONArray] Sent to {RAW_TOPIC}: {message_str}")


def send_config_update(producer):
    """发送配置更新事件"""
    print("\n=== 发送配置更新 ===")
    print("1. MQTT JSON 格式解析规则")
    print("2. CSV 格式解析规则")
    print("3. 管道符格式解析规则")
    print("4. 固定长度格式解析规则")
    print("5. 添加温度告警规则")
    print("6. 删除告警规则")

    choice = input("选择: ")

    event = None

    if choice == '1':
        # MQTT JSON 解析规则
        payload = {
            "gatewayType": "MQTT",
            "protocolType": "mqtt",
            "parserType": "JSON",
            "parseMode": "STRING",
            "matchExpr": "deviceCode != nil",
            "parseScript": "let m = seq.map(); m.deviceCode = rawMap.deviceCode; m.propertyCode = 'temperature'; m.value = rawMap.temperature; m.ts = rawMap.ts; return m;",
            "sampleInput": '{"gatewayType":"MQTT","deviceCode":"dev_001","temperature":25.5,"ts":1690000000}',
            "version": 1,
            "enabled": True
        }
        event = {
            "type": "PARSE_RULE",
            "op": "UPDATE",
            "payload": payload
        }

    elif choice == '2':
        # CSV 解析规则
        payload = {
            "gatewayType": "CSV",
            "protocolType": "tcp",
            "parserType": "CSV",
            "parseMode": "STRING",
            "matchExpr": "length(split(rawMessage, ',')) >= 5",
            "parseScript": "let fields = split(rawMessage, ','); let m = seq.map(); m.deviceCode = fields[0]; m.ts = long(fields[1]); m.value = double(fields[2]); return m;",
            "mappingScript": "let out = seq.map(); out.deviceCode = parsed.deviceCode; out.propertyCode = 'temperature'; out.value = parsed.value; out.ts = parsed.ts; return out;",
            "sampleInput": "dev_004,1771913823000,26.5,55.2,1013.25",
            "validationRegex": "^[A-Za-z0-9_]+,\\d+,\\d+\\.\\d+",
            "version": 1,
            "enabled": True
        }
        event = {
            "type": "PARSE_RULE",
            "op": "UPDATE",
            "payload": payload
        }

    elif choice == '3':
        # 管道符解析规则
        payload = {
            "gatewayType": "PIPE_DELIMITED",
            "protocolType": "tcp",
            "parserType": "PIPE",
            "parseMode": "STRING",
            "matchExpr": "length(split(rawMessage, '\\|')) >= 3",
            "parseScript": "let parts = split(rawMessage, '\\|'); let m = seq.map(); m.deviceCode = parts[0]; m.ts = long(parts[1]) * 1000; m.value = double(parts[2]); return m;",
            "mappingScript": "let out = seq.map(); out.deviceCode = parsed.deviceCode; out.propertyCode = 'temperature'; out.value = parsed.value; out.ts = parsed.ts; return out;",
            "sampleInput": "dev_005|1771913823|26.7",
            "validationRegex": "^[A-Za-z0-9_]+\\|\\d+\\|\\d+\\.\\d+",
            "version": 1,
            "enabled": True
        }
        event = {
            "type": "PARSE_RULE",
            "op": "UPDATE",
            "payload": payload
        }

    elif choice == '4':
        # 固定长度解析规则
        payload = {
            "gatewayType": "FIXED_WIDTH",
            "protocolType": "tcp",
            "parserType": "FIXED_WIDTH",
            "parseMode": "STRING",
            "matchExpr": "length(rawMessage) >= 24",
            "parseScript": "let m = seq.map(); m.deviceCode = trim(substring(rawMessage, 0, 7)); m.ts = long(substring(rawMessage, 7, 20)); m.value = double(substring(rawMessage, 20, 24)); return m;",
            "mappingScript": "let out = seq.map(); out.deviceCode = parsed.deviceCode; out.propertyCode = 'temperature'; out.value = parsed.value; out.ts = parsed.ts; return out;",
            "sampleInput": "dev003169000000026.50",
            "validationRegex": "^[a-z0-9]+\\d{13}\\d+\\.\\d+",
            "version": 1,
            "enabled": True
        }
        event = {
            "type": "PARSE_RULE",
            "op": "UPDATE",
            "payload": payload
        }

    elif choice == '5':
        # 添加告警规则
        payload = {
            "ruleCode": f"TEMP_HIGH_{int(time.time())}",
            "deviceCode": "dev_001",
            "propertyCode": "temperature",
            "conditionExpr": "value > 35",
            "triggerType": "CONTINUOUS_N",
            "triggerN": 3,
            "suppressSeconds": 10,
            "level": 2,
            "description": "温度连续3次超过35°C",
            "enabled": True
        }
        event = {
            "type": "ALARM_RULE",
            "op": "UPDATE",
            "payload": payload
        }

    elif choice == '6':
        # 删除告警规则
        rule_code = input("输入要删除的规则编码: ")
        payload = {
            "ruleCode": rule_code,
            "propertyCode": "temperature"
        }
        event = {
            "type": "ALARM_RULE",
            "op": "DELETE",
            "payload": payload
        }

    if event:
        producer.send(CONFIG_TOPIC, json.dumps(event))
        print(f"\n[Config] Sent to {CONFIG_TOPIC}: {json.dumps(event, indent=2)}")
        producer.flush()


def main():
    """主菜单"""
    producer = get_producer()
    if not producer:
        return

    print("\n" + "="*50)
    print("IoT 数据中台 - Kafka 消息模拟工具")
    print("="*50)

    while True:
        print("\n=== 主菜单 ===")
        print("1. JSON 格式 - 正常数据")
        print("2. JSON 格式 - 高温数据 (模拟告警)")
        print("3. CSV 格式 - 正常数据")
        print("4. CSV 格式 - 高温数据 (模拟告警)")
        print("5. 管道符格式 - 正常数据")
        print("6. 管道符格式 - 高温数据 (模拟告警)")
        print("7. 固定长度格式 - 正常数据")
        print("8. 固定长度格式 - 高温数据 (模拟告警)")
        print("9. JSON 数组格式 (多设备)")
        print("10. 发送配置更新")
        print("11. 连续发送混合数据 (循环测试)")
        print("0. 退出")

        choice = input("\n选择选项: ").strip()

        if choice == '0':
            break
        elif choice == '1':
            send_json_data(producer, high_temp=False)
        elif choice == '2':
            send_json_data(producer, high_temp=True)
        elif choice == '3':
            send_csv_data(producer, high_temp=False)
        elif choice == '4':
            send_csv_data(producer, high_temp=True)
        elif choice == '5':
            send_pipe_data(producer, high_temp=False)
        elif choice == '6':
            send_pipe_data(producer, high_temp=True)
        elif choice == '7':
            send_fixed_width_data(producer, high_temp=False)
        elif choice == '8':
            send_fixed_width_data(producer, high_temp=True)
        elif choice == '9':
            send_json_array_data(producer)
        elif choice == '10':
            send_config_update(producer)
        elif choice == '11':
            # 循环发送各种格式数据
            count = int(input("发送次数 (默认10): ") or 10)
            print(f"\n开始循环发送 {count} 条消息...\n")
            formats = [
                ("JSON", lambda: send_json_data(producer, random.random() < 0.3)),
                ("CSV", lambda: send_csv_data(producer, random.random() < 0.3)),
                ("PIPE", lambda: send_pipe_data(producer, random.random() < 0.3)),
                ("FIXED", lambda: send_fixed_width_data(producer, random.random() < 0.3))
            ]
            for i in range(count):
                fmt, send_func = random.choice(formats)
                print(f"\n[{i+1}/{count}] {fmt} format:")
                send_func()
                producer.flush()
                time.sleep(random.uniform(0.5, 1.5))
            print(f"\n已完成 {count} 条消息发送")

        if choice != '10' and choice != '11':  # 非配置更新和循环发送时刷新
            producer.flush()

    producer.close()
    print("\n已关闭连接")


if __name__ == "__main__":
    main()
