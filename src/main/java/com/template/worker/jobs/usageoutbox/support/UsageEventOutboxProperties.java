package com.template.worker.jobs.usageoutbox.support;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.dabom.messaging.kafka.contract.KafkaTopics;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "batch.jobs.usage-event-outbox")
public class UsageEventOutboxProperties {

    @Min(1)
    private int batchSize = 100;

    @Min(1)
    private int concurrency = 8;

    @Min(1)
    private int maxRetry = 5;

    @NotNull private Duration retryInitialDelay = Duration.ofMinutes(1);

    @NotNull private Duration retryMaxDelay = Duration.ofMinutes(16);

    @NotNull private Duration retryEligibilityDelay = Duration.ofMinutes(1);

    @NotNull private Duration publishTimeout = Duration.ofSeconds(10);

    @NotBlank private String topic = KafkaTopics.NOTIFICATION;
}
