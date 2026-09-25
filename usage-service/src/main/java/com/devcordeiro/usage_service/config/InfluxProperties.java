package com.devcordeiro.usage_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "influx")
public record InfluxProperties(
        String url,
        String token,
        String org,
        String bucket
) {
}
