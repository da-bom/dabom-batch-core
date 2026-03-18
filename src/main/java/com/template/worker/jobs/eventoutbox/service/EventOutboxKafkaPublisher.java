package com.template.worker.jobs.eventoutbox.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.dabom.messaging.kafka.contract.KafkaTopics;
import com.dabom.messaging.kafka.error.KafkaMessageProcessingException;
import com.dabom.messaging.kafka.event.KafkaEventMessageSupport;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;
import com.dabom.messaging.kafka.event.dto.notification.NotificationEventSupport;
import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.template.worker.jobs.eventoutbox.support.EventOutboxProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EventOutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaEventMessageSupport kafkaEventMessageSupport;
    private final EventOutboxProperties properties;

    public void publish(String key, NotificationPayload payload) {
        EventEnvelope<NotificationPayload> envelope = NotificationEventSupport.toEnvelope(payload);
        String message = kafkaEventMessageSupport.serialize(envelope);

        try {
            kafkaTemplate
                    .send(KafkaTopics.NOTIFICATION, key, message)
                    .get(properties.getPublishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KafkaMessageProcessingException(
                    "Interrupted while publishing usage event outbox", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new KafkaMessageProcessingException(
                    "Failed to publish usage event outbox", exception);
        }
    }
}
