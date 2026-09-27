package com.devcordeiro.ingestion_service.controller;

import com.devcordeiro.ingestion_service.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    void acceptsACompleteReadingAndPublishesIt() throws Exception {
        send("""
                {"deviceId": 1, "energyConsumed": 1.25, "timestamp": "2026-09-27T12:00:00Z"}
                """)
                .andExpect(status().isCreated());

        verify(ingestionService).ingestEnergyUsage(any());
    }

    @Test
    void rejectsAReadingWithoutDeviceIdSoItNeverReachesInflux() throws Exception {
        send("""
                {"energyConsumed": 1.25, "timestamp": "2026-09-27T12:00:00Z"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(ingestionService, never()).ingestEnergyUsage(any());
    }

    @Test
    void rejectsAReadingWithoutTimestamp() throws Exception {
        send("""
                {"deviceId": 1, "energyConsumed": 1.25}
                """)
                .andExpect(status().isBadRequest());

        verify(ingestionService, never()).ingestEnergyUsage(any());
    }

    @Test
    void rejectsNegativeConsumption() throws Exception {
        send("""
                {"deviceId": 1, "energyConsumed": -0.5, "timestamp": "2026-09-27T12:00:00Z"}
                """)
                .andExpect(status().isBadRequest());

        verify(ingestionService, never()).ingestEnergyUsage(any());
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/ingestion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
