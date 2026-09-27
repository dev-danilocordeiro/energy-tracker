package com.devcordeiro.usage_service.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record UsageDto(
        Long userId,
        List<DeviceDto> devices
) {
}
