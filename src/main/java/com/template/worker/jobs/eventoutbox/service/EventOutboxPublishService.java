package com.template.worker.jobs.eventoutbox.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.template.worker.jobs.eventoutbox.model.EventOutboxRow;
import com.template.worker.jobs.eventoutbox.query.EventOutboxQueryRepository;
import com.template.worker.jobs.eventoutbox.support.EventOutboxProperties;
import com.template.worker.jobs.eventoutbox.support.EventOutboxRetryPolicy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EventOutboxPublishService {

    private final EventOutboxQueryRepository repository;
    private final EventOutboxPayloadMapper payloadMapper;
    private final EventOutboxKafkaPublisher kafkaPublisher;
    private final EventOutboxRetryPolicy retryPolicy;
    private final EventOutboxProperties properties;
    private final ExecutorService eventOutboxExecutor;
    private final EventOutboxMetrics metrics;

    public EventOutboxPublishService(
            EventOutboxQueryRepository repository,
            EventOutboxPayloadMapper payloadMapper,
            EventOutboxKafkaPublisher kafkaPublisher,
            EventOutboxRetryPolicy retryPolicy,
            EventOutboxProperties properties,
            ExecutorService eventOutboxExecutor,
            EventOutboxMetrics metrics) {
        this.repository = repository;
        this.payloadMapper = payloadMapper;
        this.kafkaPublisher = kafkaPublisher;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.eventOutboxExecutor = eventOutboxExecutor;
        this.metrics = metrics;
    }

    public int publishPendingOutboxes() {
        LocalDateTime retryEligibleBefore =
                LocalDateTime.now().minus(properties.getRetryEligibilityDelay());
        List<EventOutboxRow> rows =
                repository.pollPublishableRows(
                        properties.getBatchSize(), properties.getMaxRetry(), retryEligibleBefore);

        if (rows.isEmpty()) {
            return 0;
        }

        List<CompletableFuture<Void>> futures =
                rows.stream()
                        .map(
                                row ->
                                        CompletableFuture.runAsync(
                                                () -> processRow(row), eventOutboxExecutor))
                        .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return rows.size();
    }

    private void processRow(EventOutboxRow row) {
        LocalDateTime now = LocalDateTime.now();

        try {
            NotificationPayload notificationPayload = payloadMapper.readPayload(row.payloadJson());
            kafkaPublisher.publish(row.eventId(), notificationPayload);
            repository.markSent(row.id(), now);
            metrics.recordSuccess();
        } catch (Exception exception) {
            int nextRetryCount = row.retryCount() + 1;
            String lastError = resolveErrorMessage(exception);

            if (retryPolicy.isRetryable(exception) && retryPolicy.canRetry(nextRetryCount)) {
                LocalDateTime nextRetryAt = retryPolicy.nextRetryAt(nextRetryCount, now);
                repository.markPendingForRetry(
                        row.id(), nextRetryCount, nextRetryAt, lastError, now);
                metrics.recordRetry(nextRetryCount);
            } else {
                repository.markFailed(row.id(), nextRetryCount, lastError, now);
                metrics.recordFailure(nextRetryCount);
            }

            log.warn(
                    "Failed to publish usage event outbox. outboxId={} eventId={} retryCount={}",
                    row.id(),
                    row.eventId(),
                    nextRetryCount,
                    exception);
        }
    }

    private String resolveErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }
}
