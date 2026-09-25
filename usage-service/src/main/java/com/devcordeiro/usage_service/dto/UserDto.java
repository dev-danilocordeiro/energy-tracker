package com.devcordeiro.usage_service.dto;

import lombok.Builder;

@Builder
public record UserDto(
        Long id,
        String name,
        String email,
        String surname,
        String address,
        boolean alerting,
        double energyAlertingThreshold
) {
}
