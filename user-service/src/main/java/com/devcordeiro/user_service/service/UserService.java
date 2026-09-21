package com.devcordeiro.user_service.service;

import com.devcordeiro.user_service.dto.UserDto;
import com.devcordeiro.user_service.entity.User;
import com.devcordeiro.user_service.exception.UserNotFoundException;
import com.devcordeiro.user_service.repository.UserRespository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {

    private final UserRespository userRespository;

    public UserService(UserRespository userRespository) {
        this.userRespository = userRespository;
    }
    public UserDto createUser(UserDto input) {
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
        return userRespository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new UserNotFoundException("User not found with id " + id));
    }

    public void updateUser(Long id, UserDto dto) {
        User user = userRespository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id " + id));

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setSurname(dto.getSurname());
        user.setAddress(dto.getAddress());
        user.setAlerting(dto.isAlerting());
        user.setEnergyAlertingThreshold(dto.getEnergyAlertingThreshold());
        userRespository.save(user);
    }

    public void deleteUser(Long id) {
        User user = userRespository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id " + id));
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
