package com.smartroom.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroom.backend.domain.DeviceTelemetry;
import com.smartroom.backend.repository.DeviceTelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class ThingsBoardService {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardService.class);

    private final RestTemplate restTemplate;
    private final DeviceTelemetryRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${thingsboard.url}")
    private String thingsBoardUrl;

    @Value("${thingsboard.device-id}")
    private String deviceId;

    @Value("${thingsboard.api-key}")
    private String apiKey;

    public ThingsBoardService(
            RestTemplate restTemplate,
            DeviceTelemetryRepository repository,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplate;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
            fixedDelayString = "${thingsboard.poll-interval-ms:10000}",
            initialDelayString = "${thingsboard.poll-initial-delay-ms:5000}")
    public void pollTelemetry() {
        try {
            DeviceTelemetry saved = fetchAndStoreTelemetry();
            if (saved != null) {
                log.info("Stored latest ThingsBoard telemetry for device {} at {}: temperature={}, humidity={}, deviceStatus={}",
                        saved.getDeviceId(),
                        saved.getRecordedAt(),
                        saved.getTemperature(),
                        saved.getHumidity(),
                        saved.getDeviceStatus());
            }
        } catch (Exception e) {
            log.error("ThingsBoard telemetry polling failed for device {}", deviceId, e);
        }
    }

    public DeviceTelemetry fetchAndStoreTelemetry() {
        String url = thingsBoardUrl
                + "/api/plugins/telemetry/DEVICE/"
                + deviceId
                + "/values/timeseries"
                + "?keys=temperature,humidity,raspberry_pi_status";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Authorization", "ApiKey " + apiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getBody() == null || response.getBody().isBlank()) {
                log.warn("Received empty response from ThingsBoard for device {}", deviceId);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            BigDecimal temperature = getDecimalValue(root, "temperature");
            BigDecimal humidity = getDecimalValue(root, "humidity");
            String deviceStatus = getStringValue(root, "raspberry_pi_status");

            Long timestamp = getTimestamp(root,
                    "temperature",
                    "humidity",
                    "raspberry_pi_status");

            LocalDateTime recordedAt = timestamp != null
                    ? LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestamp),
                            ZoneId.systemDefault())
                    : LocalDateTime.now();

            DeviceTelemetry telemetry = DeviceTelemetry.builder()
                    .deviceId(deviceId)
                    .temperature(temperature)
                    .humidity(humidity)
                    .deviceStatus(deviceStatus)
                    .recordedAt(recordedAt)
                    .build();

            return repository.save(telemetry);

        } catch (Exception e) {
            log.error("Failed to process ThingsBoard telemetry for device {}", deviceId, e);
            return null;
        }
    }

    private BigDecimal getDecimalValue(JsonNode root, String key) {

        JsonNode values = root.path(key);

        if (!values.isArray() || values.isEmpty()) {
            return null;
        }

        String value = values.get(0)
                .path("value")
                .asText(null);

        if (value == null || value.isBlank()) {
            return null;
        }

        return new BigDecimal(value);
    }

    private String getStringValue(JsonNode root, String key) {

        JsonNode values = root.path(key);

        if (!values.isArray() || values.isEmpty()) {
            return null;
        }

        return values.get(0)
                .path("value")
                .asText(null);
    }

    private Long getTimestamp(JsonNode root, String... keys) {

        for (String key : keys) {

            JsonNode values = root.path(key);

            if (values.isArray() && !values.isEmpty()) {

                JsonNode timestamp = values.get(0).path("ts");

                if (timestamp.isNumber()) {
                    return timestamp.asLong();
                }
            }
        }

        return null;
    }
}