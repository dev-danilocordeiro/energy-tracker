package com.devcordeiro.user_service.service;

import com.devcordeiro.user_service.dto.UserDto;
import com.devcordeiro.user_service.entity.User;
import com.devcordeiro.user_service.repository.UserRespository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class UserService {

    private final UserRespository userRespository;

    public UserService(UserRespository userRespository) {
        this.userRespository = userRespository;
    }
    public UserDto createUser(UserDto input) {
        log.info("Creating user: {}",  input);
        final User createdUser = User.builder()
                .name(input.getName())
                .email(input.getEmail())
                .surname(input.getSurname())
                .address(input.getAddress())
                .alerting(input.isAlerting())
                .energyAlertingThreshold(input.getEnergyAlertingThreshold())
                .build();
        final User saved = userRespository.save(createdUser);
        return toDto(saved);
    }

    public UserDto getUserById(Long id) {
        log.info("Retrieving user by id: {}", id);
        return userRespository.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    public void updateUser(Long id, UserDto dto) {
        log.info("Updating user: {}",  dto);
        User user = userRespository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setSurname(dto.getSurname());
        user.setAddress(dto.getAddress());
        user.setAlerting(dto.isAlerting());
        user.setEnergyAlertingThreshold(dto.getEnergyAlertingThreshold());
        userRespository.save(user);
    }

    public void deleteUser(Long id) {
        log.info("Deleting user: {}", id);
        User user = userRespository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        userRespository.delete(user);
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .surname(user.getSurname())
                .address(user.getAddress())
                .alerting(user.isAlerting())
                .energyAlertingThreshold(user.getEnergyAlertingThreshold())
                .build();
    }
}
