package com.devcordeiro.insight_service.service;

import com.devcordeiro.insight_service.client.UsageClient;
import com.devcordeiro.insight_service.dto.DeviceDto;
import com.devcordeiro.insight_service.dto.InsightDto;
import com.devcordeiro.insight_service.dto.UsageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock
    private UsageClient usageClient;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private InsightService insightService;

    @BeforeEach
    void setUp() {
        insightService = new InsightService(usageClient, chatClient);
    }

    @Test
    void savingTipsForAUserWithoutDevicesSkipOllamaAndReportZeroUsage() {
        when(usageClient.getXDaysUsageForUser(10L, 3)).thenReturn(new UsageDto(10L, List.of()));

        InsightDto insight = insightService.getSavingTips(10L);

        assertThat(insight.userId()).isEqualTo(10L);
        assertThat(insight.energyUsage()).isZero();
        assertThat(insight.tips()).contains("Nenhum dispositivo");
        verifyNoInteractions(chatClient);
    }

    @Test
    void overviewForAUserWhoseDevicesComeBackNullSkipsOllamaInsteadOfFailing() {
        when(usageClient.getXDaysUsageForUser(10L, 3)).thenReturn(new UsageDto(10L, null));

        InsightDto insight = insightService.getOverview(10L);

        assertThat(insight.energyUsage()).isZero();
        verifyNoInteractions(chatClient);
    }

    @Test
    void savingTipsSendTheTotalConsumptionInKwhThroughTheChatClient() {
        when(usageClient.getXDaysUsageForUser(10L, 3)).thenReturn(new UsageDto(10L, List.of(
                device("Termostato", 10.0), device("Lampada", 2.5))));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(chatClient.prompt().user(prompt.capture()).call().content()).thenReturn("dicas");

        InsightDto insight = insightService.getSavingTips(10L);

        assertThat(insight.tips()).isEqualTo("dicas");
        assertThat(insight.energyUsage()).isEqualTo(12.5);
        assertThat(prompt.getValue()).contains("12.50 kWh");
    }

    @Test
    void overviewListsEveryDeviceWithItsConsumption() {
        when(usageClient.getXDaysUsageForUser(10L, 3)).thenReturn(new UsageDto(10L, List.of(
                device("Termostato", 10.0), device("Lampada", 2.5))));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(chatClient.prompt().user(prompt.capture()).call().content()).thenReturn("resumo");

        insightService.getOverview(10L);

        assertThat(prompt.getValue())
                .contains("- Termostato (THERMOSTAT, Sala): 10.00 kWh")
                .contains("- Lampada (THERMOSTAT, Sala): 2.50 kWh");
    }

    private static DeviceDto device(String name, double energyConsumed) {
        return new DeviceDto(1L, name, "THERMOSTAT", "Sala", energyConsumed);
    }
}
