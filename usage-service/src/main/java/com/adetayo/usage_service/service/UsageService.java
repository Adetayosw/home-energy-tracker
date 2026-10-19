package com.adetayo.usage_service.service;


import com.adetayo.kafka.event.AlertingEvent;
import com.adetayo.kafka.event.EnergyUsageEvent;
import com.adetayo.usage_service.client.DeviceClient;
import com.adetayo.usage_service.client.UserClient;
import com.adetayo.usage_service.dto.DeviceDto;
import com.adetayo.usage_service.dto.UsageDto;
import com.adetayo.usage_service.dto.UserDto;
import com.adetayo.usage_service.model.Device;
import com.adetayo.usage_service.model.DeviceEnergy;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UsageService {

    private final InfluxDBClient influxDBClient;
    private final DeviceClient deviceClient;
    private final UserClient userClient;

    @Value("${influx.bucket}")
    private String influxBucket;

    @Value("${influx.org}")
    private String influxOrg;

    private final KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    public UsageService(InfluxDBClient influxDBClient,
                        DeviceClient deviceClient,  UserClient userClient,
    KafkaTemplate<String, AlertingEvent> kafkaTemplate
    ) {
        this.influxDBClient = influxDBClient;
        this.deviceClient = deviceClient;
        this.userClient = userClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics="energy-usage", groupId= "usage-service")
    public void energyUsageEvent(EnergyUsageEvent energyUsageEvent){
//        log.info("Received energy usage event: {}", energyUsageEvent);
        Point point = Point.measurement("energy-usage")
                .addTag("deviceId", String.valueOf(energyUsageEvent.deviceId()))
                .addField("energyConsumed", energyUsageEvent.energyConsumed())
                .time(energyUsageEvent.timestamp(), WritePrecision.MS);
        influxDBClient.getWriteApiBlocking().writePoint(influxBucket, influxOrg, point);
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void aggregateDeviceEnergyUsage(){
        final Instant now = Instant.now();
        final Instant oneHourAgo = now.minusSeconds(3600);

        List<FluxTable> tables = getFluxTables(oneHourAgo, now);

        List<DeviceEnergy> deviceEnergies = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                String deviceIdStr = (String) record.getValueByKey("deviceId");
                double energyConsumed = record.getValueByKey("_value") instanceof Number ?
                        ((Number) Objects.requireNonNull(record.getValueByKey("_value"))).doubleValue() : 0.0;

                assert deviceIdStr != null;
                deviceEnergies.add(
                        DeviceEnergy.builder()
                                .deviceId(Long.valueOf(deviceIdStr))
                                .energyConsumed(energyConsumed)
                                .build()
                );
            }
        }
        log.info("Aggregated device energies over the past hour: {}", deviceEnergies);

        for(DeviceEnergy deviceEnergy : deviceEnergies){
            final DeviceDto deviceResponse =  deviceClient.getDeviceById(deviceEnergy.getDeviceId());

            if (deviceResponse == null){
                continue;
            }
            deviceEnergy.setUserId(deviceResponse.userId());
        }
        // Remove devices with null userId

        deviceEnergies.removeIf(deviceEnergy -> deviceEnergy.getUserId() == null);

        //Get user device mapping and aggregate per user
        Map<Long,List<DeviceEnergy>> userDeviceEnergyMap = deviceEnergies.stream()
                .collect(Collectors.groupingBy(DeviceEnergy::getUserId));
        log.info("User-device Energy map: {}", userDeviceEnergyMap);

        // Get user energy consumption threshold
        List<Long> userIds = new ArrayList<>(userDeviceEnergyMap.keySet());
        final Map<Long, Double> userThresholdMap = new HashMap<>();
        final Map<Long, String> userEmailMap = new HashMap<>();

        for (final Long userId: userIds) {
            try {
                UserDto user = userClient.getUserById(userId);
                if (user != null && user.id() != null && user.alerting()) {
                    userThresholdMap.put(userId, user.energyAlertingThreshold());
                    userEmailMap.put(userId, user.email());
                } else {
                    log.warn("User not found or alerting disabled for ID: {}", userId);
                }

            } catch (Exception e) {
                log.warn("Failed to fetch user threshold value for ID: {}", userId);
            }
        }

        log.info("User-threshold map: {}", userThresholdMap);

        // Check thresholds against aggregated usage
        final List<Long> alertedUsers = new ArrayList<>(userDeviceEnergyMap.keySet());
        for(final Long userId: alertedUsers){
            final Double threshold = userThresholdMap.get(userId);
            if (threshold == null){
                continue;
            }
            final List<DeviceEnergy> devices = userDeviceEnergyMap.get(userId);

            final double totalConsumption = devices.stream().mapToDouble(DeviceEnergy::getEnergyConsumed).sum();

            if(totalConsumption > threshold){
                log.info("ALERT: User ID {} has exceeded the energy threshold! " + "Total Consumption: {}, Threshold: {}",
                        userId, totalConsumption, threshold);

                final AlertingEvent alertingEvent = AlertingEvent.builder()
                        .userId(userId)
                        .message("Energy consumption threshold exceeded")
                        .threshold(threshold)
                        .energyConsumed(totalConsumption)
                        .email(userEmailMap.get(userId))
                        .build();

                kafkaTemplate.send("energy-alerts", alertingEvent);


            }
        }

    }

    private @NonNull List<FluxTable> getFluxTables(Instant oneHourAgo, Instant now) {
        String fluxQuery = String.format("""
        from(bucket: "%s")
          |> range(start: time(v: "%s"), stop: time(v: "%s"))
          |> filter(fn: (r) => r["_measurement"] == "energy-usage")
          |> filter(fn: (r) => r["_field"] == "energyConsumed")
          |> group(columns: ["deviceId"])
          |> sum(column: "_value")
        """, influxBucket, oneHourAgo.toString(), now);

        QueryApi queryApi = influxDBClient.getQueryApi();

        return queryApi.query(fluxQuery, influxOrg);
    }

    public UsageDto getXDaysUsageForUser(Long userId, int days){
        log.info("Getting {} days usage for userId: {}", days, userId);

        final List<DeviceDto> devicesDto = deviceClient.getAllDevicesForUser(userId);

        final List<Device>  devices = new ArrayList<>();

        for(DeviceDto deviceDto : devicesDto){
            devices.add(Device.builder()
                    .userId(deviceDto.userId())
                    .id(deviceDto.id())
                    .name(deviceDto.name())
                    .location(deviceDto.location())
                    .type(deviceDto.type())
                    .build()
            );
        }

        if(devices.isEmpty() || devices == null){
            return UsageDto.builder()
                    .userId(userId)
                    .devices(null)
                    .build();
        }

        List<String> deviceIdStrings = devices.stream()
                .map(Device::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();

        final Instant now = Instant.now();

        final Instant start = now.minusSeconds((long) days * 24 * 3600);

        final String deviceFilter = deviceIdStrings.stream()
                .map(idStr -> String.format("r[\"deviceId\"] == \"%s\"", idStr))
                .collect(Collectors.joining(" or "));
        String fluxQuery = String.format("""
        from(bucket: "%s")
          |> range(start: time(v: "%s"), stop: time(v: "%s"))
          |> filter(fn: (r) => r["_measurement"] == "energy_usage")
          |> filter(fn: (r) => r["_field"] == "energyConsumed")
          |> filter(fn: (r) => %s)
          |> group(columns: ["deviceId"])
          |> sum(column: "_value")
        """, influxBucket, start.toString(), now.toString(), deviceFilter);

        final Map<Long, Double> aggregatedMap = new HashMap<>();

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Object deviceIdObj = record.getValueByKey("deviceId");
                    String deviceIdStr = deviceIdObj == null ? null : deviceIdObj.toString();
                    if (deviceIdStr == null) continue;

                    Double energyConsumed = record.getValueByKey("_value") instanceof Number
                            ? ((Number) record.getValueByKey("_value")).doubleValue()
                            : 0.0;

                    try {
                        Long deviceId = Long.valueOf(deviceIdStr);
                        aggregatedMap.put(deviceId, aggregatedMap.getOrDefault(deviceId, 0.0) + energyConsumed);
                    } catch (NumberFormatException nfe) {
                        log.warn("Failed to parse deviceId from flux record: {}", deviceIdStr);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query InfluxDB for user {} usage over {} days: {}", userId, days, e.getMessage());
            // set aggregatedConsumption to 0.0 on error
            devices.forEach(d -> d.setEnergyConsumed(0.0));
            return UsageDto.builder()
                    .userId(userId)
                    .devices(null)
                    .build();
        }

        // populate aggregated energy consumed per device
        for (Device device : devices) {
            if (device == null || device.getId() == null) continue;
            device.setEnergyConsumed(aggregatedMap.getOrDefault(device.getId(), 0.0));
        }

        log.info("Aggregated energy consumption for userId {}: {}", userId, aggregatedMap);

        final List<DeviceDto> resultDevices = devices.stream()
                .map(d -> DeviceDto.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .type(d.getType())
                        .location(d.getLocation())
                        .userId(d.getUserId())
                        .energyConsumed(d.getEnergyConsumed())
                        .build())
                .toList();

        return UsageDto.builder()
                .userId(userId)
                .devices(resultDevices)
                .build();

    }
}
