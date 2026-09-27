package com.devcordeiro.usage_service.client;

import com.devcordeiro.usage_service.dto.DeviceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
public class DeviceClient {

    private final RestClient restClient;

    public DeviceClient(@Value("${device.service.url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public Optional<DeviceDto> findById(Long deviceId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/{deviceId}", deviceId)
                    .retrieve()
                    .body(DeviceDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public List<DeviceDto> getAllDevicesForUser(Long userId) {
        List<DeviceDto> devices = restClient.get()
                .uri("/user/{userId}", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<DeviceDto>>() {});

        return devices != null ? devices : List.of();
    }
}
