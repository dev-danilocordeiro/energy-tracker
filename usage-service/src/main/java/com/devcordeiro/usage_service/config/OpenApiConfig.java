package com.devcordeiro.usage_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI usageServiceApiDocs() {
        return new OpenAPI()
                .info(new Info().title("Usage Service API")
                        .description("Usage Service API for Home Energy Tracker")
                        .version("1.0.0"))
                // Relativo: o Swagger UI resolve contra a origem da pagina, entao o
                // "Try it out" passa pelo gateway quando os docs sao abertos por ele.
                .servers(List.of(new Server().url("/")));
    }
}
