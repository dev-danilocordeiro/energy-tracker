package com.devcordeiro.usage_service.consumer;

import com.devcordeiro.kafka.event.EnergyUsageEvent;
import com.devcordeiro.usage_service.repository.EnergyUsageRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class EnergyUsageConsumer {

    private final EnergyUsageRepository energyUsageRepository;

    public EnergyUsageConsumer(EnergyUsageRepository energyUsageRepository) {
        this.energyUsageRepository = energyUsageRepository;
    }

    @KafkaListener(topics = "energy-usage", groupId = "usage-service")
    public void onEnergyUsage(@Payload EnergyUsageEvent event) {
        energyUsageRepository.save(event);
    }
}
