package com.devcordeiro.insight_service.service;

import com.devcordeiro.insight_service.client.UsageClient;
import com.devcordeiro.insight_service.config.OllamaConfig;
import com.devcordeiro.insight_service.dto.DeviceDto;
import com.devcordeiro.insight_service.dto.InsightDto;
import com.devcordeiro.insight_service.dto.UsageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
public class InsightService {

    private final UsageClient usageClient;
    private final OllamaChatModel ollamaChatModel;

    public InsightService(UsageClient usageClient,
                          OllamaChatModel ollamaChatModel) {
        this.usageClient = usageClient;
        this.ollamaChatModel = ollamaChatModel;
    }

    public InsightDto getSavingTips (Long userId) {
        // Fetch data from Usage Service
        final UsageDto usageData = usageClient.getXDaysUsageForUser(userId, 3);

        double totalUsage = usageData.devices().stream()
                .mapToDouble(DeviceDto::energyConsumed)
                .sum();

        log.info ("Calling Ollama for userId {} with total usage {}",
                userId, totalUsage);

        String prompt = """
                This is my total household energy consumption over the past 3 days: %.2f kWh.
                How can I reduce my energy consumption? How does it compare to an average household?
                Answer in Brazilian Portuguese.
                """.formatted(totalUsage);

        ChatResponse response = ollamaChatModel.call(
                Prompt.builder()
                        .content(prompt)
                        .build());

        return InsightDto.builder()
                .userId(userId)
                .tips(response.getResult().getOutput().getText())
                .energyUsage(totalUsage)
                .build();
    }
    public InsightDto getOverview(Long userId) {
        final UsageDto usageData = usageClient.getXDaysUsageForUser(userId, 3);
        double totalUsage = usageData.devices().stream()
                .mapToDouble(DeviceDto::energyConsumed)
                .sum();

        log.info("Calling Ollama for userId {} with total usage {}", userId, totalUsage);

        String deviceUsage = usageData.devices().stream()
                .map(device -> "- %s (%s, %s): %.2f kWh".formatted(
                        device.name(), device.type(), device.location(), device.energyConsumed()))
                .collect(Collectors.joining("\n"));

        String prompt = """
                Analyse the following energy usage data and provide a concise overview with actionable insights.
                The data is the aggregated consumption per device over the past 3 days.
                Answer in Brazilian Portuguese.

                Usage data:
                %s
                """.formatted(deviceUsage);

        ChatResponse response = ollamaChatModel.call(
                Prompt.builder()
                        .content(prompt)
                        .build());

        return InsightDto.builder()
                .userId(userId)
                .tips(response.getResult().getOutput().getText())
                .energyUsage(totalUsage)
                .build();
    }
}
