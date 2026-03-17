package com.template.worker.jobs.usageoutbox.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.dabom.messaging.kafka.error.KafkaMessageProcessingException;
import com.dabom.messaging.kafka.event.KafkaEventMessageSupport;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;
import com.dabom.messaging.kafka.event.dto.notification.NotificationEventSupport;
import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.template.worker.jobs.usageoutbox.support.UsageEventOutboxProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UsageEventOutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaEventMessageSupport kafkaEventMessageSupport;
    private final UsageEventOutboxProperties properties;

    public void publish(String key, NotificationPayload payload) {
        EventEnvelope<NotificationPayload> envelope = NotificationEventSupport.toEnvelope(payload);
        String message = kafkaEventMessageSupport.serialize(envelope);

        try {
            kafkaTemplate
                    .send(properties.getTopic(), key, message)
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
