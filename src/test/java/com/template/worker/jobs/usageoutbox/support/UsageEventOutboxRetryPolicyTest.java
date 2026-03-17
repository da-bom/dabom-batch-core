package com.template.worker.jobs.usageoutbox.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.NetworkException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class UsageEventOutboxRetryPolicyTest {

    @Test
    @DisplayName("nextRetryAt - capped exponential backoff를 적용한다")
    void nextRetryAt_appliesCappedExponentialBackoff() {
        UsageEventOutboxProperties properties = new UsageEventOutboxProperties();
        properties.setRetryInitialDelay(Duration.ofMinutes(1));
        properties.setRetryMaxDelay(Duration.ofMinutes(16));
        properties.setMaxRetry(5);

        UsageEventOutboxRetryPolicy retryPolicy = new UsageEventOutboxRetryPolicy(properties);
        LocalDateTime now = LocalDateTime.of(2026, 3, 16, 10, 0);

        assertThat(retryPolicy.nextRetryAt(1, now)).isEqualTo(now.plusMinutes(1));
        assertThat(retryPolicy.nextRetryAt(2, now)).isEqualTo(now.plusMinutes(2));
        assertThat(retryPolicy.nextRetryAt(3, now)).isEqualTo(now.plusMinutes(4));
        assertThat(retryPolicy.nextRetryAt(6, now)).isEqualTo(now.plusMinutes(16));
        assertThat(retryPolicy.canRetry(4)).isTrue();
        assertThat(retryPolicy.canRetry(5)).isFalse();
    }

    @Test
    @DisplayName("isRetryable - validation 오류는 non-retryable, Kafka 전송 오류는 retryable이다")
    void isRetryable_classifiesExceptions() {
        UsageEventOutboxRetryPolicy retryPolicy =
                new UsageEventOutboxRetryPolicy(new UsageEventOutboxProperties());

        assertThat(retryPolicy.isRetryable(new IllegalArgumentException("bad payload"))).isFalse();
        assertThat(retryPolicy.isRetryable(new JsonProcessingException("bad json") {})).isFalse();
        assertThat(retryPolicy.isRetryable(new TimeoutException("timeout"))).isTrue();
        assertThat(retryPolicy.isRetryable(new NetworkException("network"))).isTrue();
        assertThat(retryPolicy.isRetryable(new KafkaException("kafka"))).isTrue();
        assertThat(retryPolicy.isRetryable(new ExecutionException(new TimeoutException("timeout"))))
                .isTrue();
        assertThat(
                        retryPolicy.isRetryable(
                                new CompletionException(
                                        new IllegalArgumentException("bad payload"))))
                .isFalse();
        assertThat(retryPolicy.isRetryable(new RuntimeException("unknown"))).isFalse();
    }
}
