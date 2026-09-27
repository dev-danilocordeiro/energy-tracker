package com.devcordeiro.usage_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Device {
    private Long id;
    private String name;
    private String type;
    private String location;
    private Long userId;
    private Double energyConsumed;
}
