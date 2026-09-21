package com.devcordeiro.user_service.dto;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String surname;
    private String address;
    private boolean alerting;
    private double energyAlertingThreshold;
}
