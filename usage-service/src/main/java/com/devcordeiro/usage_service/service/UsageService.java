package com.devcordeiro.usage_service.service;

import com.devcordeiro.kafka.event.AlertingEvent;
import com.devcordeiro.usage_service.client.DeviceClient;
import com.devcordeiro.usage_service.client.UserClient;
import com.devcordeiro.usage_service.dto.DeviceDto;
import com.devcordeiro.usage_service.dto.UserDto;
import com.devcordeiro.usage_service.model.DeviceEnergy;
import com.devcordeiro.usage_service.repository.EnergyUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class UsageService {

    static final String ALERTS_TOPIC = "energy-alerts";
    static final Duration AGGREGATION_WINDOW = Duration.ofHours(1);

    private final EnergyUsageRepository energyUsageRepository;
    private final DeviceClient deviceClient;
    private final UserClient userClient;
    private final KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    public UsageService(EnergyUsageRepository energyUsageRepository,
                        DeviceClient deviceClient,
                        UserClient userClient,
                        KafkaTemplate<String, AlertingEvent> kafkaTemplate) {
        this.energyUsageRepository = energyUsageRepository;
        this.deviceClient = deviceClient;
        this.userClient = userClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void checkEnergyThresholds() {
        Instant now = Instant.now();
        List<DeviceEnergy> deviceEnergies = energyUsageRepository.sumEnergyByDevice(now.minus(AGGREGATION_WINDOW), now);
        log.info("Aggregated energy for {} devices over the past hour", deviceEnergies.size());

        Map<Long, Double> energyByUser = sumEnergyByUser(deviceEnergies);
        log.info("Aggregated energy per user: {}", energyByUser);

        energyByUser.forEach(this::alertIfOverThreshold);
    }

    private Map<Long, Double> sumEnergyByUser(List<DeviceEnergy> deviceEnergies) {
        Map<Long, Double> energyByUser = new HashMap<>();
        for (DeviceEnergy deviceEnergy : deviceEnergies) {
            findDevice(deviceEnergy.deviceId())
                    .map(DeviceDto::userId)
                    .ifPresent(userId -> energyByUser.merge(userId, deviceEnergy.energyConsumed(), Double::sum));
        }
        return energyByUser;
    }

    private void alertIfOverThreshold(Long userId, double totalConsumption) {
        Optional<UserDto> user = findUser(userId).filter(UserDto::alerting);
        if (user.isEmpty()) {
            log.debug("User {} not found or alerting disabled", userId);
            return;
        }

        double threshold = user.get().energyAlertingThreshold();
        if (totalConsumption <= threshold) {
            log.info("User {} is within threshold. Total consumption {}, threshold {}", userId, totalConsumption, threshold);
            return;
        }

        log.info("ALERT: user {} exceeded energy threshold. Total consumption {}, threshold {}", userId, totalConsumption, threshold);
        AlertingEvent alertingEvent = AlertingEvent.builder()
                .userId(userId)
                .message("Energy consumption threshold exceeded")
                .threshold(threshold)
                .energyConsumed(totalConsumption)
                .email(user.get().email())
                .build();
        kafkaTemplate.send(ALERTS_TOPIC, alertingEvent);
    }

    // A failing lookup skips that device or user instead of aborting the whole cycle
    private Optional<DeviceDto> findDevice(Long deviceId) {
        try {
            Optional<DeviceDto> device = deviceClient.findById(deviceId);
            if (device.isEmpty()) {
                log.warn("Device {} does not exist", deviceId);
            }
            return device;
        } catch (RestClientException e) {
            log.warn("Could not fetch device {}: {}", deviceId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<UserDto> findUser(Long userId) {
        try {
            return userClient.findById(userId);
        } catch (RestClientException e) {
            log.warn("Could not fetch user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
