package com.devcordeiro.usage_service.service;

import com.devcordeiro.kafka.event.AlertingEvent;
import com.devcordeiro.usage_service.client.DeviceClient;
import com.devcordeiro.usage_service.client.UserClient;
import com.devcordeiro.usage_service.dto.DeviceDto;
import com.devcordeiro.usage_service.dto.UsageDto;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
    private UsageService usageService;

    @BeforeEach
    void setUp() {
        usageService = new UsageService(energyUsageRepository, deviceClient, userClient, kafkaTemplate, clock);
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

    @Test
    void alertsAUserOnlyOnceWhileTheyStayOverTheThresholdWithinTheCooldown() {
        givenDeviceEnergies(new DeviceEnergy(1L, 150.0));
        givenDevice(1L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();
        clock.advance(Duration.ofSeconds(10));
        usageService.checkEnergyThresholds();
        clock.advance(UsageService.ALERT_COOLDOWN.minusSeconds(20));
        usageService.checkEnergyThresholds();

        verify(kafkaTemplate, times(1)).send(eq(UsageService.ALERTS_TOPIC), any(AlertingEvent.class));
    }

    @Test
    void alertsTheSameUserAgainOnceTheCooldownHasPassed() {
        givenDeviceEnergies(new DeviceEnergy(1L, 150.0));
        givenDevice(1L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();
        clock.advance(UsageService.ALERT_COOLDOWN);
        usageService.checkEnergyThresholds();

        verify(kafkaTemplate, times(2)).send(eq(UsageService.ALERTS_TOPIC), any(AlertingEvent.class));
    }

    @Test
    void theCooldownOfOneUserDoesNotSilenceAnother() {
        givenDeviceEnergies(new DeviceEnergy(1L, 150.0));
        givenDevice(1L, 10L);
        givenUser(10L, true, 100.0);
        usageService.checkEnergyThresholds();

        givenDeviceEnergies(new DeviceEnergy(1L, 150.0), new DeviceEnergy(2L, 150.0));
        givenDevice(2L, 20L);
        givenUser(20L, true, 100.0);
        clock.advance(Duration.ofSeconds(10));
        usageService.checkEnergyThresholds();

        ArgumentCaptor<AlertingEvent> alerts = ArgumentCaptor.forClass(AlertingEvent.class);
        verify(kafkaTemplate, times(2)).send(eq(UsageService.ALERTS_TOPIC), alerts.capture());
        assertThat(alerts.getAllValues()).extracting(AlertingEvent::userId).containsExactly(10L, 20L);
    }

    @Test
    void reusesTheDeviceOwnerAcrossRunsInsteadOfCallingDeviceServiceEveryTime() {
        givenDeviceEnergies(new DeviceEnergy(1L, 40.0));
        givenDevice(1L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();
        clock.advance(Duration.ofSeconds(10));
        usageService.checkEnergyThresholds();

        verify(deviceClient, times(1)).findById(1L);
    }

    @Test
    void looksTheDeviceOwnerUpAgainOnceTheCacheExpires() {
        givenDeviceEnergies(new DeviceEnergy(1L, 40.0));
        givenDevice(1L, 10L);
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();
        clock.advance(UsageService.DEVICE_OWNER_TTL);
        usageService.checkEnergyThresholds();

        verify(deviceClient, times(2)).findById(1L);
    }

    @Test
    void retriesADeviceWhoseLookupFailedOnTheNextRun() {
        givenDeviceEnergies(new DeviceEnergy(1L, 150.0));
        when(deviceClient.findById(1L))
                .thenThrow(new ResourceAccessException("connection refused"))
                .thenReturn(Optional.of(DeviceDto.builder().id(1L).userId(10L).build()));
        givenUser(10L, true, 100.0);

        usageService.checkEnergyThresholds();
        clock.advance(Duration.ofSeconds(10));
        usageService.checkEnergyThresholds();

        verify(kafkaTemplate, times(1)).send(eq(UsageService.ALERTS_TOPIC), any(AlertingEvent.class));
    }

    @Test
    void returnsAnEmptyDeviceListWhenTheUserHasNoDevices() {
        when(deviceClient.getAllDevicesForUser(10L)).thenReturn(List.of());

        UsageDto usage = usageService.getXDaysUsageForUser(10L, 3);

        assertThat(usage.userId()).isEqualTo(10L);
        assertThat(usage.devices()).isEmpty();
        verify(energyUsageRepository, never()).sumEnergyForDevices(any(), any(), any());
    }

    @Test
    void reportsEachDevicesConsumptionAndZeroForDevicesWithoutReadings() {
        when(deviceClient.getAllDevicesForUser(10L)).thenReturn(List.of(
                DeviceDto.builder().id(1L).userId(10L).build(),
                DeviceDto.builder().id(2L).userId(10L).build()));
        when(energyUsageRepository.sumEnergyForDevices(eq(List.of(1L, 2L)), any(), any()))
                .thenReturn(List.of(new DeviceEnergy(1L, 42.5)));

        UsageDto usage = usageService.getXDaysUsageForUser(10L, 3);

        assertThat(usage.devices())
                .extracting(DeviceDto::id, DeviceDto::energyConsumed)
                .containsExactly(tuple(1L, 42.5), tuple(2L, 0.0));
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

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
