package com.devcordeiro.usage_service.service;

import com.devcordeiro.kafka.event.AlertingEvent;
import com.devcordeiro.usage_service.client.DeviceClient;
import com.devcordeiro.usage_service.client.UserClient;
import com.devcordeiro.usage_service.dto.DeviceDto;
import com.devcordeiro.usage_service.dto.UsageDto;
import com.devcordeiro.usage_service.dto.UserDto;
import com.devcordeiro.usage_service.model.Device;
import com.devcordeiro.usage_service.model.DeviceEnergy;
import com.devcordeiro.usage_service.repository.EnergyUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UsageService {

    static final String ALERTS_TOPIC = "energy-alerts";
    static final Duration AGGREGATION_WINDOW = Duration.ofHours(1);
    // The check runs every 10 seconds over a 1 hour window, so without a cooldown a user
    // above the threshold would be alerted on every run
    static final Duration ALERT_COOLDOWN = AGGREGATION_WINDOW;

    private final EnergyUsageRepository energyUsageRepository;
    private final DeviceClient deviceClient;
    private final UserClient userClient;
    private final KafkaTemplate<String, AlertingEvent> kafkaTemplate;
    private final Clock clock;
    // In memory, so a restart may send one extra alert per user
    private final Map<Long, Instant> lastAlertByUser = new ConcurrentHashMap<>();

    public UsageService(EnergyUsageRepository energyUsageRepository,
                        DeviceClient deviceClient,
                        UserClient userClient,
                        KafkaTemplate<String, AlertingEvent> kafkaTemplate,
                        Clock clock) {
        this.energyUsageRepository = energyUsageRepository;
        this.deviceClient = deviceClient;
        this.userClient = userClient;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void checkEnergyThresholds() {
        Instant now = clock.instant();
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

        Instant now = clock.instant();
        Instant lastAlert = lastAlertByUser.get(userId);
        if (lastAlert != null && now.isBefore(lastAlert.plus(ALERT_COOLDOWN))) {
            log.debug("User {} is over threshold but was already alerted at {}", userId, lastAlert);
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
        lastAlertByUser.put(userId, now);
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

    public UsageDto getXDaysUsageForUser(Long userId, int days) {
        log.info("Getting usage for userId {} over past {} days", userId, days);
        final List<DeviceDto> devicesDto = deviceClient.getAllDevicesForUser(userId);

        final List<Device> devices = new ArrayList<>();
        for (DeviceDto deviceDto : devicesDto) {
            devices.add(Device.builder()
                    .id(deviceDto.id())
                    .name(deviceDto.name())
                    .type(deviceDto.type())
                    .location(deviceDto.location())
                    .userId(deviceDto.userId())
                    .build());
        }

        if (devices.isEmpty()) {
            return UsageDto.builder()
                    .userId(userId)
                    .devices(List.of())
                    .build();
        }

        final List<Long> deviceIds = devices.stream()
                .map(Device::getId)
                .filter(Objects::nonNull)
                .toList();

        final Instant now = clock.instant();
        final Instant start = now.minus(Duration.ofDays(days));

        final Map<Long, Double> aggregatedMap = new HashMap<>();
        for (DeviceEnergy deviceEnergy : energyUsageRepository.sumEnergyForDevices(deviceIds, start, now)) {
            aggregatedMap.merge(deviceEnergy.deviceId(), deviceEnergy.energyConsumed(), Double::sum);
        }

        for (Device device : devices) {
            if (device.getId() == null) continue;
            device.setEnergyConsumed(aggregatedMap.getOrDefault(device.getId(), 0.0));
        }

        log.info("Aggregated energy consumption for userId {}: {}", userId, aggregatedMap);

        final List<DeviceDto> resultDevices = devices.stream()
                .map(d -> DeviceDto.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .type(d.getType())
                        .location(d.getLocation())
                        .userId(d.getUserId())
                        .energyConsumed(d.getEnergyConsumed())
                        .build())
                .toList();

        return UsageDto.builder()
                .userId(userId)
                .devices(resultDevices)
                .build();
    }
}
