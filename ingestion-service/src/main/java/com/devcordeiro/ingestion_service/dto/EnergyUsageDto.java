package com.devcordeiro.ingestion_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.time.Instant;

@Builder
public record EnergyUsageDto(
        @NotNull @Positive
        Long deviceId,
        @PositiveOrZero
        double energyConsumed,
        @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
)  {
}
