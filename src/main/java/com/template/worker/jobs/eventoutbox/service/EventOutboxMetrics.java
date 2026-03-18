package com.template.worker.jobs.eventoutbox.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.eventoutbox.query.EventOutboxQueryRepository;
import com.template.worker.jobs.eventoutbox.support.EventOutboxProperties;
import com.template.worker.jobs.eventoutbox.support.EventOutboxStatus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class EventOutboxMetrics {

    private final Counter publishSuccessCounter;
    private final Counter publishRetryCounter;
    private final Counter publishFailureCounter;
    private final DistributionSummary retryCountSummary;
    private final EventOutboxQueryRepository repository;
    private final EventOutboxProperties properties;

    public EventOutboxMetrics(
            MeterRegistry meterRegistry,
            EventOutboxQueryRepository repository,
            EventOutboxProperties properties) {
        this.repository = repository;
        this.properties = properties;
        this.publishSuccessCounter =
                Counter.builder("batch.usage_event_outbox.publish.success")
                        .description("Successful usage event outbox publishes")
                        .register(meterRegistry);
        this.publishRetryCounter =
                Counter.builder("batch.usage_event_outbox.publish.retry")
                        .description("Retryable usage event outbox publish failures")
                        .register(meterRegistry);
        this.publishFailureCounter =
                Counter.builder("batch.usage_event_outbox.publish.failure")
                        .description("Final usage event outbox publish failures")
                        .register(meterRegistry);
        this.retryCountSummary =
                DistributionSummary.builder("batch.usage_event_outbox.retry.count")
                        .description("Retry count distribution for usage event outbox")
                        .register(meterRegistry);

        Gauge.builder(
                        "batch.usage_event_outbox.backlog",
                        repository,
                        value -> value.countByStatus(EventOutboxStatus.PUBLISH_PENDING))
                .description("Pending usage event outbox backlog")
                .register(meterRegistry);
        Gauge.builder(
                        "batch.usage_event_outbox.failed",
                        repository,
                        value -> value.countByStatus(EventOutboxStatus.FAILED))
                .description("Failed usage event outbox rows")
                .register(meterRegistry);
        Gauge.builder(
                        "batch.usage_event_outbox.sent",
                        repository,
                        value -> value.countByStatus(EventOutboxStatus.SENT))
                .description("Sent usage event outbox rows")
                .register(meterRegistry);
        Gauge.builder(
                        "batch.usage_event_outbox.oldest_pending_age.seconds",
                        this,
                        EventOutboxMetrics::resolveOldestPendingAgeSeconds)
                .description(
                        "Age in seconds of the oldest publishable pending usage event outbox row")
                .register(meterRegistry);
    }

    public void recordSuccess() {
        publishSuccessCounter.increment();
    }

    public void recordRetry(int retryCount) {
        publishRetryCounter.increment();
        retryCountSummary.record(retryCount);
    }

    public void recordFailure(int retryCount) {
        publishFailureCounter.increment();
        retryCountSummary.record(retryCount);
    }

    private double resolveOldestPendingAgeSeconds() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oldestCreatedAt =
                repository.findOldestPendingCreatedAt(
                        now.minus(properties.getRetryEligibilityDelay()));
        if (oldestCreatedAt == null) {
            return 0D;
        }
        return ChronoUnit.SECONDS.between(oldestCreatedAt, now);
    }
}
