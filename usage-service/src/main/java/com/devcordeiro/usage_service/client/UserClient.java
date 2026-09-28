package com.devcordeiro.usage_service.client;

import com.devcordeiro.usage_service.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class UserClient {

    private final RestClient restClient;

    // The Boot-managed builder is instrumented: it opens a client span and sends traceparent
    public UserClient(RestClient.Builder restClientBuilder, @Value("${user.service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public Optional<UserDto> findById(Long userId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/{userId}", userId)
                    .retrieve()
                    .body(UserDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
