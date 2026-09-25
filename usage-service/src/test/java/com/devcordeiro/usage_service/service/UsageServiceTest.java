package com.devcordeiro.usage_service.service;

import com.devcordeiro.kafka.event.AlertingEvent;
import com.devcordeiro.usage_service.client.DeviceClient;
import com.devcordeiro.usage_service.client.UserClient;
import com.devcordeiro.usage_service.dto.DeviceDto;
import com.devcordeiro.usage_service.dto.UserDto;
import com.devcordeiro.usage_service.model.DeviceEnergy;
import com.devcordeiro.usage_service.repository.EnergyUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock
    private EnergyUsageRepository energyUsageRepository;
    @Mock
    private DeviceClient deviceClient;
    @Mock
    private UserClient userClient;
    @Mock
    private KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    private UsageService usageService;

    @BeforeEach
    void setUp() {
        usageService = new UsageService(energyUsageRepository, deviceClient, userClient, kafkaTemplate);
    }

    @Test
    void publishesAnAlertWhenTheSumOfAUsersDevicesExceedsTheirThreshold() {
        givenDeviceEnergies(new DeviceEnergy(1L, 60.0), new DeviceEnergy(2L, 50.0));
        givenDevice(1L, 10L);
        givenDevice(2L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();

        ArgumentCaptor<AlertingEvent> alert = ArgumentCaptor.forClass(AlertingEvent.class);
        verify(kafkaTemplate).send(eq(UsageService.ALERTS_TOPIC), alert.capture());
        assertThat(alert.getValue().userId()).isEqualTo(10L);
        assertThat(alert.getValue().energyConsumed()).isEqualTo(110.0);
        assertThat(alert.getValue().threshold()).isEqualTo(100.0);
        assertThat(alert.getValue().email()).isEqualTo("user10@example.com");
    }

    @Test
    void doesNotAlertWhenConsumptionIsWithinTheThreshold() {
        givenDeviceEnergies(new DeviceEnergy(1L, 40.0));
        givenDevice(1L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();

        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void doesNotAlertUsersWhoHaveAlertingDisabled() {
        givenDeviceEnergies(new DeviceEnergy(1L, 500.0));
        givenDevice(1L, 10L);
        givenUser(10L, false, 100.0);

        usageService.checkEnergyThresholds();

        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void skipsAnUnknownDeviceAndStillAlertsForTheOthers() {
        givenDeviceEnergies(new DeviceEnergy(99L, 1000.0), new DeviceEnergy(1L, 150.0));
        when(deviceClient.findById(99L)).thenReturn(Optional.empty());
        givenDevice(1L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();

        ArgumentCaptor<AlertingEvent> alert = ArgumentCaptor.forClass(AlertingEvent.class);
        verify(kafkaTemplate).send(eq(UsageService.ALERTS_TOPIC), alert.capture());
        assertThat(alert.getValue().energyConsumed()).isEqualTo(150.0);
    }

    @Test
    void keepsCheckingOtherDevicesWhenTheDeviceServiceFailsForOne() {
        givenDeviceEnergies(new DeviceEnergy(1L, 500.0), new DeviceEnergy(2L, 150.0));
        when(deviceClient.findById(1L)).thenThrow(new ResourceAccessException("connection refused"));
        givenDevice(2L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();

        ArgumentCaptor<AlertingEvent> alert = ArgumentCaptor.forClass(AlertingEvent.class);
        verify(kafkaTemplate).send(eq(UsageService.ALERTS_TOPIC), alert.capture());
        assertThat(alert.getValue().energyConsumed()).isEqualTo(150.0);
    }

    private void givenDeviceEnergies(DeviceEnergy... deviceEnergies) {
        when(energyUsageRepository.sumEnergyByDevice(any(), any())).thenReturn(List.of(deviceEnergies));
    }

    private void givenDevice(Long deviceId, Long userId) {
        when(deviceClient.findById(deviceId))
                .thenReturn(Optional.of(DeviceDto.builder().id(deviceId).userId(userId).build()));
    }

    private void givenUser(Long userId, boolean alerting, double threshold) {
        when(userClient.findById(userId)).thenReturn(Optional.of(UserDto.builder()
                .id(userId)
                .email("user" + userId + "@example.com")
                .alerting(alerting)
                .energyAlertingThreshold(threshold)
                .build()));
    }
}
