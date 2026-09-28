package com.devcordeiro.ingestion_service.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Boot builds the OpenTelemetry SDK with the OTLP log exporter, but the Logback appender
 * declared in logback-spring.xml is created before the context exists, so it has to be
 * handed the SDK once Boot has built it. Until then, nothing reaches Loki.
 */
@Component
public class OpenTelemetryConfig implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryConfig(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
