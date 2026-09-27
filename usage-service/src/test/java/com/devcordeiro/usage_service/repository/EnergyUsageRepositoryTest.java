package com.devcordeiro.usage_service.repository;

import com.devcordeiro.usage_service.config.InfluxProperties;
import com.devcordeiro.usage_service.model.DeviceEnergy;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyUsageRepositoryTest {

    @Mock
    private InfluxDBClient influxClient;
    @Mock
    private QueryApi queryApi;

    private EnergyUsageRepository repository;

    @BeforeEach
    void setUp() {
        when(influxClient.getQueryApi()).thenReturn(queryApi);
        repository = new EnergyUsageRepository(influxClient, new InfluxProperties("http://influx", "token", "org", "bucket"));
    }

    @Test
    void skipsReadingsWithAMalformedDeviceIdInsteadOfFailingTheWholeQuery() {
        givenRecords(record("1", 10.0), record("null", 99.0), record("2", 5.5));

        List<DeviceEnergy> energies = repository.sumEnergyByDevice(Instant.EPOCH, Instant.now());

        assertThat(energies).containsExactly(new DeviceEnergy(1L, 10.0), new DeviceEnergy(2L, 5.5));
    }

    private void givenRecords(FluxRecord... records) {
        FluxTable table = new FluxTable();
        table.getRecords().addAll(List.of(records));
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));
    }

    private static FluxRecord record(String deviceId, double value) {
        FluxRecord record = new FluxRecord(0);
        record.getValues().put("deviceId", deviceId);
        record.getValues().put("_value", value);
        return record;
    }
}
