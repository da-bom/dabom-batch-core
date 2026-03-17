package com.template.worker.jobs.usageoutbox.service;

import org.springframework.stereotype.Component;

import com.dabom.messaging.kafka.error.NonRetryableKafkaMessageProcessingException;
import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UsageEventOutboxPayloadMapper {

    private final ObjectMapper objectMapper;

    public NotificationPayload readPayload(String payloadJson) {
        try {
            NotificationPayload payload =
                    objectMapper.readValue(payloadJson, NotificationPayload.class);
            validate(payload);
            return payload;
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaMessageProcessingException(
                    "Failed to deserialize usage event outbox payload", exception);
        }
    }

    private void validate(NotificationPayload payload) {
        if (payload == null) {
            throw new NonRetryableKafkaMessageProcessingException(
                    "Outbox payload must not be null.");
        }
        if (payload.familyId() == null) {
            throw new NonRetryableKafkaMessageProcessingException(
                    "Outbox payload familyId must not be null.");
        }
        if (payload.customerId() == null) {
            throw new NonRetryableKafkaMessageProcessingException(
                    "Outbox payload customerId must not be null.");
        }
        if (payload.type() == null) {
            throw new NonRetryableKafkaMessageProcessingException(
                    "Outbox payload type must not be null.");
        }
        if (isBlank(payload.title())) {
            throw new NonRetryableKafkaMessageProcessingException(
                    "Outbox payload title must not be blank.");
        }
        if (isBlank(payload.message())) {
            throw new NonRetryableKafkaMessageProcessingException(
                    "Outbox payload message must not be blank.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
