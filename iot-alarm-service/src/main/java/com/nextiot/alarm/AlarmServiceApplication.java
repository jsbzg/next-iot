package com.nextiot.alarm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * IoT 告警服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.nextiot")
@EnableKafka
@MapperScan("com.nextiot.alarm.mapper")
public class AlarmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlarmServiceApplication.class, args);
    }
}