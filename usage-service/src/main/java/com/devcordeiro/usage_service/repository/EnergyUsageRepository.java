package com.devcordeiro.usage_service.repository;

import com.devcordeiro.kafka.event.EnergyUsageEvent;
import com.devcordeiro.usage_service.config.InfluxProperties;
import com.devcordeiro.usage_service.model.DeviceEnergy;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class EnergyUsageRepository {

    // Shared by writes and queries so the two can never drift apart
    private static final String MEASUREMENT = "energy-usage";
    private static final String DEVICE_ID_TAG = "deviceId";
    private static final String ENERGY_FIELD = "energyConsumed";

    private final InfluxDBClient influxClient;
    private final InfluxProperties influxProperties;

    public EnergyUsageRepository(InfluxDBClient influxClient, InfluxProperties influxProperties) {
        this.influxClient = influxClient;
        this.influxProperties = influxProperties;
    }

    public void save(EnergyUsageEvent event) {
        Point point = Point.measurement(MEASUREMENT)
                .addTag(DEVICE_ID_TAG, String.valueOf(event.deviceId()))
                .addField(ENERGY_FIELD, event.energyConsumed())
                .time(event.timestamp(), WritePrecision.MS);
        influxClient.getWriteApiBlocking().writePoint(influxProperties.bucket(), influxProperties.org(), point);
    }

    public List<DeviceEnergy> sumEnergyByDevice(Instant start, Instant stop) {
        String fluxQuery = """
                from(bucket: "%s")
                |> range(start: time(v: "%s"), stop: time(v: "%s"))
                |> filter(fn: (r) => r["_measurement"] == "%s")
                |> filter(fn: (r) => r["_field"] == "%s")
                |> group(columns: ["%s"])
                |> sum(column: "_value")
                """.formatted(influxProperties.bucket(), start, stop, MEASUREMENT, ENERGY_FIELD, DEVICE_ID_TAG);

        return influxClient.getQueryApi().query(fluxQuery, influxProperties.org()).stream()
                .flatMap(table -> table.getRecords().stream())
                .map(this::toDeviceEnergy)
                .toList();
    }

    private DeviceEnergy toDeviceEnergy(FluxRecord record) {
        Long deviceId = Long.valueOf((String) record.getValueByKey(DEVICE_ID_TAG));
        double energyConsumed = record.getValue() instanceof Number value ? value.doubleValue() : 0.0;
        return new DeviceEnergy(deviceId, energyConsumed);
    }
}
