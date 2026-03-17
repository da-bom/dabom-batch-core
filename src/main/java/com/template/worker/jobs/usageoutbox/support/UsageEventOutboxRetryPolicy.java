package com.template.worker.jobs.usageoutbox.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JacksonException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UsageEventOutboxRetryPolicy {

    private final UsageEventOutboxProperties properties;

    public LocalDateTime nextRetryAt(int nextRetryCount, LocalDateTime now) {
        Duration initialDelay = properties.getRetryInitialDelay();
        Duration maxDelay = properties.getRetryMaxDelay();

        long multiplier = 1L << Math.max(0, nextRetryCount - 1);
        long initialMillis = initialDelay.toMillis();
        long candidateMillis;

        try {
            candidateMillis = Math.multiplyExact(initialMillis, multiplier);
        } catch (ArithmeticException exception) {
            candidateMillis = Long.MAX_VALUE;
        }

        long delayMillis = Math.min(candidateMillis, maxDelay.toMillis());
        return now.plusNanos(Duration.ofMillis(delayMillis).toNanos());
    }

    public boolean canRetry(int currentRetryCount) {
        return currentRetryCount < properties.getMaxRetry();
    }

    public boolean isRetryable(Exception exception) {
        Throwable current = unwrap(exception);

        while (current != null) {
            if (current instanceof IllegalArgumentException
                    || current instanceof JacksonException) {
                return false;
            }
            if (current instanceof TimeoutException
                    || current instanceof RetriableException
                    || current instanceof KafkaException) {
                return true;
            }
            current = unwrap(current.getCause());
        }
        return false;
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof ExecutionException || throwable instanceof CompletionException) {
            return throwable.getCause() == null ? throwable : throwable.getCause();
        }
        return throwable;
    }
}
