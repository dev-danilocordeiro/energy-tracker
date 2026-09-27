package com.devcordeiro.insight_service.service;

import com.devcordeiro.insight_service.client.UsageClient;
import com.devcordeiro.insight_service.dto.DeviceDto;
import com.devcordeiro.insight_service.dto.InsightDto;
import com.devcordeiro.insight_service.dto.UsageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InsightService {

    static final int DAYS = 3;

    private final UsageClient usageClient;
    private final ChatClient chatClient;

    public InsightService(UsageClient usageClient, ChatClient chatClient) {
        this.usageClient = usageClient;
        this.chatClient = chatClient;
    }

    public InsightDto getSavingTips(Long userId) {
        final List<DeviceDto> devices = fetchDevices(userId);
        if (devices.isEmpty()) {
            return noDevicesInsight(userId);
        }

        final double totalUsage = totalUsage(devices);
        final String prompt = String.format(Locale.ROOT, """
                This is my total household energy consumption over the past %d days: %.2f kWh.
                How can I reduce my energy consumption? How does it compare to an average household?
                """, DAYS, totalUsage);

        return insight(userId, totalUsage, prompt);
    }

    public InsightDto getOverview(Long userId) {
        final List<DeviceDto> devices = fetchDevices(userId);
        if (devices.isEmpty()) {
            return noDevicesInsight(userId);
        }

        final String deviceUsage = devices.stream()
                .map(device -> String.format(Locale.ROOT, "- %s (%s, %s): %.2f kWh",
                        device.name(), device.type(), device.location(), device.energyConsumed()))
                .collect(Collectors.joining("\n"));
        final String prompt = """
                Analyse the following energy usage data and provide a concise overview with actionable insights.
                The data is the aggregated consumption per device over the past %d days.

                Usage data:
                %s
                """.formatted(DAYS, deviceUsage);

        return insight(userId, totalUsage(devices), prompt);
    }

    private List<DeviceDto> fetchDevices(Long userId) {
        final UsageDto usageData = usageClient.getXDaysUsageForUser(userId, DAYS);
        if (usageData == null || usageData.devices() == null || usageData.devices().isEmpty()) {
            log.info("User {} has no devices, skipping Ollama", userId);
            return List.of();
        }
        return usageData.devices();
    }

    private static double totalUsage(List<DeviceDto> devices) {
        return devices.stream()
                .mapToDouble(DeviceDto::energyConsumed)
                .sum();
    }

    private InsightDto insight(Long userId, double totalUsage, String prompt) {
        log.info("Calling Ollama for userId {} with total usage {}", userId, totalUsage);
        final String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return InsightDto.builder()
                .userId(userId)
                .tips(answer)
                .energyUsage(totalUsage)
                .build();
    }

    private InsightDto noDevicesInsight(Long userId) {
        return InsightDto.builder()
                .userId(userId)
                .tips("Nenhum dispositivo cadastrado para este usuario. Cadastre um dispositivo para receber insights de consumo.")
                .energyUsage(0.0)
                .build();
    }
}
