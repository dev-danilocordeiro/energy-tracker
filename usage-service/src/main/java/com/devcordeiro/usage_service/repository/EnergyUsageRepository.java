package com.devcordeiro.usage_service.repository;

import com.devcordeiro.kafka.event.EnergyUsageEvent;
import com.devcordeiro.usage_service.config.InfluxProperties;
import com.devcordeiro.usage_service.model.DeviceEnergy;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
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
        return sumEnergy(start, stop, "true");
    }

    public List<DeviceEnergy> sumEnergyForDevices(Collection<Long> deviceIds, Instant start, Instant stop) {
        if (deviceIds.isEmpty()) {
            return List.of();
        }
        String deviceFilter = deviceIds.stream()
                .map(deviceId -> "r[\"%s\"] == \"%d\"".formatted(DEVICE_ID_TAG, deviceId))
                .collect(Collectors.joining(" or "));
        return sumEnergy(start, stop, deviceFilter);
    }

    private List<DeviceEnergy> sumEnergy(Instant start, Instant stop, String recordFilter) {
        String fluxQuery = """
                from(bucket: "%s")
                |> range(start: time(v: "%s"), stop: time(v: "%s"))
                |> filter(fn: (r) => r["_measurement"] == "%s")
                |> filter(fn: (r) => r["_field"] == "%s")
                |> filter(fn: (r) => %s)
                |> group(columns: ["%s"])
                |> sum(column: "_value")
                """.formatted(influxProperties.bucket(), start, stop, MEASUREMENT, ENERGY_FIELD, recordFilter, DEVICE_ID_TAG);

        return influxClient.getQueryApi().query(fluxQuery, influxProperties.org()).stream()
                .flatMap(table -> table.getRecords().stream())
                .map(this::toDeviceEnergy)
                .flatMap(Optional::stream)
                .toList();
    }

    // A reading with a malformed deviceId tag (such as "null" from an unvalidated request) is skipped,
    // otherwise it would fail every query whose range includes it
    private Optional<DeviceEnergy> toDeviceEnergy(FluxRecord record) {
        Object deviceIdTag = record.getValueByKey(DEVICE_ID_TAG);
        long deviceId;
        try {
            deviceId = Long.parseLong(String.valueOf(deviceIdTag));
        } catch (NumberFormatException e) {
            log.warn("Skipping energy usage with invalid deviceId tag: {}", deviceIdTag);
            return Optional.empty();
        }
        double energyConsumed = record.getValue() instanceof Number value ? value.doubleValue() : 0.0;
        return Optional.of(new DeviceEnergy(deviceId, energyConsumed));
    }
}
