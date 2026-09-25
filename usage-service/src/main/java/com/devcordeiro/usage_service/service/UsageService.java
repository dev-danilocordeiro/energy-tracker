package com.devcordeiro.usage_service.service;

import com.devcordeiro.kafka.event.AlertingEvent;
import com.devcordeiro.kafka.event.EnergyUsageEvent;
import com.devcordeiro.usage_service.client.DeviceClient;
import com.devcordeiro.usage_service.client.UserClient;
import com.devcordeiro.usage_service.dto.DeviceDto;
import com.devcordeiro.usage_service.dto.UserDto;
import com.devcordeiro.usage_service.model.DeviceEnergy;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UsageService {

    @Value("${influx.bucket}")
    private String influxBucket;

    @Value("${influx.org}")
    private String influxOrg;

    private final InfluxDBClient influxClient;
    private final DeviceClient deviceClient;
    private final UserClient userClient;
    private final KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    public UsageService(InfluxDBClient influxClient,
                        DeviceClient deviceClient,
                        UserClient userClient,
                        KafkaTemplate<String, AlertingEvent> kafkaTemplate) {
        this.influxClient = influxClient;
        this.deviceClient = deviceClient;
        this.userClient = userClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "energy-usage", groupId = "usage-service")
    public void energyUsageEvent(@Payload EnergyUsageEvent energyUsageEvent) {
        // log.info("Received energy usage event: {}", energyUsageEvent);
        Point point = Point.measurement("energy-usage")
                .addTag("deviceId", String.valueOf(energyUsageEvent.deviceId()))
                .addField("energyConsumed", energyUsageEvent.energyConsumed())
                .time(energyUsageEvent.timestamp(), WritePrecision.MS);
        influxClient.getWriteApiBlocking().writePoint(influxBucket, influxOrg, point);
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void aggregateDeviceEnergyUsageEvent() {
        log.info("Aggregating energy usage event");
        final Instant now = Instant.now();
        final Instant oneHourAgo = now.minusSeconds(3600);

        String fluxQuery = String.format("""
                from(bucket: "%s")
                |> range(start: time(v: "%s"), stop: time(v: "%s"))
                |> filter(fn: (r) => r["_measurement"] == "energy-usage")
                |> filter(fn: (r) => r["_field"] == "energyConsumed")
                |> group(columns: ["deviceId"])
                |> sum(column: "_value")
                """, influxBucket, oneHourAgo.toString(), now);

        QueryApi queryApi = influxClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);

        List<DeviceEnergy> deviceEnergies = new ArrayList<>();

        for (FluxTable table : tables) {
            for(FluxRecord record : table.getRecords()) {
                String deviceStr = (String) record.getValueByKey("deviceId");
                Double energyConsumed = record.getValueByKey("_value") instanceof Number ?
                       ((Number) record.getValueByKey("_value")).doubleValue() : 0.0;

                deviceEnergies.add(
                        DeviceEnergy.builder()
                                .deviceId(Long.valueOf(deviceStr))
                                .energyConsumed(energyConsumed)
                                .build()
                );
            }
        }
        log.info("Aggregated device energies over the past hour: {}", deviceEnergies);


        for (DeviceEnergy deviceEnergy : deviceEnergies) {
            final DeviceDto deviceResponse = deviceClient.getDeviceById(deviceEnergy.getDeviceId());
            if(deviceResponse == null || deviceResponse.id() == null) {
                log.warn("Device with id {} does not exist", deviceEnergy.getDeviceId());
                continue;
            }
            deviceEnergy.setUserId(deviceResponse.userId());
        }
        // Remove device without user
        deviceEnergies.removeIf(de -> de.getUserId() == null);

        // Get user-device mapping and aggregate per user
        Map<Long, List<DeviceEnergy>> userDeviceEnergyMap =
                deviceEnergies.stream()
                        .collect(Collectors.groupingBy(DeviceEnergy::getUserId));

        log.info("Aggregated user-device energies: {}", userDeviceEnergyMap);

        List<Long> userIds = new ArrayList<>(userDeviceEnergyMap.keySet());
        final Map<Long, Double> userThresholdMap = new HashMap<>();
        final Map<Long, String> userEmailMap = new HashMap<>();

        for(final Long userId : userIds) {
            try {
                UserDto userResponse = userClient.getUserById(userId);
                if(userResponse == null || userResponse.id() == null ||  !userResponse.alerting()) {
                    log.warn("User not found or alerting disabled for ID: {}", userId);
                    continue;
                }
                userThresholdMap.put(userId, userResponse.energyAlertingThreshold());
                userEmailMap.put(userId, userResponse.email());
            } catch (Exception e) {
                log.warn("Error occurred while getting user threshold for ID: {}", userId, e);
            }
        }
        log.info("Aggregated user-device energies over the threshold: {}", userThresholdMap);

        final List<Long> alertedUsers = new ArrayList<>(userThresholdMap.keySet());
        for(final Long userId : alertedUsers) {
            final Double threshold = userThresholdMap.get(userId);
            final List<DeviceEnergy> devices = userDeviceEnergyMap.get(userId);

            final Double totalConsumption = devices.stream()
                    .mapToDouble(DeviceEnergy::getEnergyConsumed)
                    .sum();

            if(totalConsumption > threshold) {
                log.info("ALERT: User ID {} has exceeded energy threshold! Total Consumption {}, Threshold {}", userId,
                        totalConsumption, threshold);
                // Put message on kafka alert-topic
                final AlertingEvent alertingEvent = AlertingEvent.builder()
                        .userId(userId)
                        .message("Energy consumption threshold exceeded")
                        .threshold(threshold)
                        .energyConsumed(totalConsumption)
                        .email(userEmailMap.get(userId))
                        .build();

                kafkaTemplate.send("energy-alerts", alertingEvent);
            } else {
                log.info("User ID {} is within TotalConsumption {}, Threshold {}", userId, totalConsumption, threshold);
            }
        }
    }
}
