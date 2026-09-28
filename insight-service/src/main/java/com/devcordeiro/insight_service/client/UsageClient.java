package com.devcordeiro.insight_service.client;

import com.devcordeiro.insight_service.dto.UsageDto;
import com.devcordeiro.insight_service.exception.UsageServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UsageClient {

    private final RestClient restClient;

    // The Boot-managed builder is instrumented: it opens a client span and sends traceparent
    public UsageClient(RestClient.Builder restClientBuilder, @Value("${usage.service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public UsageDto getXDaysUsageForUser(Long userId, int days) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{userId}")
                            .queryParam("days", days)
                            .build(userId))
                    .retrieve()
                    .body(UsageDto.class);
        } catch (RestClientException e) {
            throw new UsageServiceUnavailableException("Could not fetch usage for user " + userId, e);
        }
    }
}
