package com.nextiot.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * IoT 配置管理服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.nextiot")
@EnableKafka
@MapperScan("com.nextiot.config.mapper")
public class ConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}