package com.devcordeiro.usage_service.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfluxProperties.class)
public class InfluxDBConfig {

    @Bean
    public InfluxDBClient influxDBClient(InfluxProperties properties) {
        return InfluxDBClientFactory.create(
                properties.url(), properties.token().toCharArray(), properties.org()
        );
    }
}
