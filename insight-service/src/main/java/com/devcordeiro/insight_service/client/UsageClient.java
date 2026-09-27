package com.devcordeiro.insight_service.client;

import com.devcordeiro.insight_service.dto.UsageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UsageClient {

    private final RestClient restClient;

    public UsageClient(@Value("${usage.service.url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public UsageDto getXDaysUsageForUser(Long userId, int days) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{userId}")
                        .queryParam("days", days)
                        .build(userId))
                .retrieve()
                .body(UsageDto.class);
    }
}
