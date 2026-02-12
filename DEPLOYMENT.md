# 部署指南

## 环境要求

- Docker Desktop（Windows/Mac）或 Docker + Docker Compose（Linux）
- JDK 17+
- Maven 3.6+

## 一、启动基础设施

### 1.1 启动 Docker 环境

在项目根目录执行：

```bash
docker-compose up -d
```

等待所有服务启动完成，约 30-60 秒。

### 1.2 验证基础设施

访问以下 URL 验证服务状态：

- Flink Web UI: http://localhost:8081
- Kafka UI: http://localhost:8080
- MySQL: localhost:3306 (用户: flink_user, 密码: flink_pass)

## 二、构建和启动后端服务

### 2.1 编译项目

在项目根目录执行：

```bash
mvn clean install -DskipTests
```

### 2.2 启动配置服务

```bash
cd iot-config-service
java -jar target/iot-config-service-1.0.0.jar
```

配置服务将在 http://localhost:8085 启动。

### 2.3 启动告警服务

```bash
cd iot-alarm-service
java -jar target/iot-alarm-service-1.0.0.jar
```

告警服务将在 http://localhost:8086 启动。

### 2.4 部署 Flink Job

```bash
# 方式一：通过 Flink Web UI 部署
# 1. 访问 http://localhost:8081
# 2. 点击 "Submit New Job"
# 3. 上传 iot-flink-job/target/iot-flink-job-1.0.0.jar
# 4. 点击 "Submit"

# 方式二：通过 Flink CLI 部署（在 Docker 容器内）
docker exec -it flink-jobmanager /opt/flink/bin/flink run \
  -d \
  /opt/flink/usrlib/iot-flink-job-1.0.0.jar
```

## 三、访问前端页面

### 3.1 直接打开 HTML 文件

```
打开 iot-frontend/src/index.html
```

### 3.2 或使用本地 Web 服务器

```bash
# 使用 Python
cd iot-frontend/src
python -m http.server 8087

# 使用 Node.js http-server
cd iot-frontend/src
npx http-server -p 8087

# 然后访问 http://localhost:8087
```

## 四、系统验证

### 4.1 查看设备数据

前端页面会自动刷新，显示设备状态和告警信息。

### 4.2 查看告警日志

查看 Console 日志，可以看到 Flink 输出的告警事件：

```
触发条件: deviceCode=dev_001, value=85.3, count=1
触发条件: deviceCode=dev_001, value=87.5, count=2
触发条件: deviceCode=dev_001, value=89.2, count=3
触发告警: deviceCode=dev_001, value=89.2, level=CRITICAL
```

### 4.3 查看数据库

```bash
# 进入 MySQL 容器
docker exec -it mysql mysql -u flink_user -pflink_pass next_iot

# 查看告警表
SELECT * FROM alarm_instance ORDER BY last_trigger_time DESC LIMIT 10;
```

## 五、常见问题

### 5.1 MySQL 启动失败

检查 /var/lib/mysql/data 目录权限，确保 Docker 有写入权限。

### 5.2 Flink Job 启动失败

1. 检查 Kafka 是否正常启动
2. 检查 jar 包是否正确复制到 flink-job 目录
3. 查看 Flink Web UI 的日志

### 5.3 告警不显示

1. 检查 Flink Job 是否正常运行
2. 检查 Alarm Service 是否正常消费 Kafka 消息
3. 查看各个服务的日志输出

### 5.4 重置系统

```bash
# 停止所有服务
docker-compose down

# 清理数据卷
docker volume rm next-iot_zookeeper-data next-iot_kafka-data next-iot_mysql-data next-iot_flink-checkpoints next-iot_flink-savepoints

# 重新启动
docker-compose up -d
```

## 六、扩展配置

### 6.1 修改配置

编辑各服务的 `application.yml` 文件：

- iot-config-service/src/main/resources/application.yml
- iot-alarm-service/src/main/resources/application.yml
- iot-flink-job/src/main/java/.../IotFlinkJob.java (硬编码配置)

### 6.2 调整 Kafka Topics

修改 docker-compose.yml 中的 Kafka 配置：

```yaml
KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
KAFKA_NUM_PARTITIONS: 3
```

### 6.3 调整 Flink 并行度

修改 IotFlinkJob.java 中的并行度配置（或通过 Flink Web UI 调整）。

## 七、生产环境注意事项

1. **高可用配置**：配置 Kafka 和 Flink 的 HA 集群
2. **持久化存储**：使用外部存储而非 Docker volumes
3. **监控告警**：集成 Prometheus + Grafana
4. **日志收集**：使用 ELK 或 Loki 收集日志
5. **安全加固**：启用 TLS、认证授权