package com.template.worker.jobs.usageoutbox.service;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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

    /** notification payload를 직렬화해 Kafka broker ack까지 대기하며 발행한다. */
    public void publish(String key, NotificationPayload payload) throws Exception {
        EventEnvelope<NotificationPayload> envelope = NotificationEventSupport.toEnvelope(payload);
        String message = kafkaEventMessageSupport.serialize(envelope);

        // SENT 상태는 broker ack 이후에만 기록하기 위해 future 완료까지 기다린다.
        kafkaTemplate
                .send(properties.getTopic(), key, message)
                .get(properties.getPublishTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }
}
