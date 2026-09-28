package com.devcordeiro.ingestion_service.simulation;

import com.devcordeiro.ingestion_service.dto.EnergyUsageDto;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
// Off for load tests, so its ~200 req/s don't mix with the traffic being measured
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true", matchIfMissing = true)
public class ParallelDataSimulator {

    private final int parallelThreads;
    private final int requestsPerInterval;
    private final ExecutorService executorService;
    private final RestClient restClient;

    public ParallelDataSimulator(@Value("${simulation.parallel-threads}") int parallelThreads,
                                 @Value("${simulation.requests-per-interval}") int requestsPerInterval,
                                 @Value("${simulation.endpoint}") String ingestionEndpoint) {
        this.parallelThreads = parallelThreads;
        this.requestsPerInterval = requestsPerInterval;
        this.executorService = Executors.newFixedThreadPool(parallelThreads);
        this.restClient = RestClient.create(ingestionEndpoint);
        log.info("ParallelDataSimulator started with {} threads", parallelThreads);
    }

    @Scheduled(fixedRateString = "${simulation.interval-ms}")
    public void sendMockData() {
        int batchSize = requestsPerInterval / parallelThreads;
        int remainder = requestsPerInterval % parallelThreads;

        for (int i = 0; i < parallelThreads; i++) {
            int requestsForThread = batchSize + (i < remainder ? 1 : 0);
            executorService.submit(() -> {
                for (int j = 0; j < requestsForThread; j++) {
                    sendReading();
                }
            });
        }
    }

    private void sendReading() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        EnergyUsageDto energyUsageDto = EnergyUsageDto.builder()
                .deviceId(random.nextLong(1, 6))
                .energyConsumed(Math.round(random.nextDouble(0.0, 2.0) * 100.0) / 100.0)
                .timestamp(Instant.now())
                .build();

        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(energyUsageDto)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Sent energy usage request {}", energyUsageDto);
        } catch (Exception e) {
            log.error("Error while sending energy usage request {}, error: {}", energyUsageDto, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();

        log.info("ParallelDataSimulator shutdown...");
    }
}
