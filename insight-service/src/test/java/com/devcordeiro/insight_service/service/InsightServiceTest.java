package com.devcordeiro.insight_service.service;

import com.devcordeiro.insight_service.client.UsageClient;
import com.devcordeiro.insight_service.dto.InsightDto;
import com.devcordeiro.insight_service.dto.UsageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock
    private UsageClient usageClient;
    @Mock
    private OllamaChatModel ollamaChatModel;

    private InsightService insightService;

    @BeforeEach
    void setUp() {
        insightService = new InsightService(usageClient, ollamaChatModel);
    }

    @Test
    void savingTipsForAUserWithoutDevicesSkipOllamaAndReportZeroUsage() {
        when(usageClient.getXDaysUsageForUser(10L, 3)).thenReturn(new UsageDto(10L, List.of()));

        InsightDto insight = insightService.getSavingTips(10L);

        assertThat(insight.userId()).isEqualTo(10L);
        assertThat(insight.energyUsage()).isZero();
        assertThat(insight.tips()).contains("Nenhum dispositivo");
        verify(ollamaChatModel, never()).call(any(Prompt.class));
    }

    @Test
    void overviewForAUserWhoseDevicesComeBackNullSkipsOllamaInsteadOfFailing() {
        when(usageClient.getXDaysUsageForUser(10L, 3)).thenReturn(new UsageDto(10L, null));

        InsightDto insight = insightService.getOverview(10L);

        assertThat(insight.energyUsage()).isZero();
        verify(ollamaChatModel, never()).call(any(Prompt.class));
    }
}
