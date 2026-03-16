package com.template.worker.global.retry;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import lombok.Getter;

@Component
@Getter
public class BatchRetrySupport {

    // Retry limit/backoff와 재시도 대상 예외를 한곳에서 관리해 Step/Tasklet 정책을 맞춤
    private final int retryLimit;
    private final long backOffMillis;

    public BatchRetrySupport(
            @Value("${batch.retry.limit}") int retryLimit,
            @Value("${batch.retry.backoff-millis}") long backOffMillis) {
        Assert.isTrue(retryLimit > 0, "batch.retry.limit must be greater than zero");
        Assert.isTrue(backOffMillis >= 0L, "batch.retry.backoff-millis must be zero or greater");
        this.retryLimit = retryLimit;
        this.backOffMillis = backOffMillis;
    }

    public RetryTemplate createDbRetryTemplate() {
        // Tasklet에서 DB 계열 일시 오류 재시도에 사용함
        return createRetryTemplate(buildRetryableExceptions(false));
    }

    public RetryTemplate createRedisRetryTemplate() {
        // Tasklet에서 Redis 포함 일시 오류 재시도에 사용함
        return createRetryTemplate(buildRetryableExceptions(true));
    }

    public <I, O> FaultTolerantStepBuilder<I, O> applyDbRetry(
            FaultTolerantStepBuilder<I, O> builder) {
        return applyRetry(builder, false);
    }

    public <I, O> FaultTolerantStepBuilder<I, O> applyRedisRetry(
            FaultTolerantStepBuilder<I, O> builder) {
        return applyRetry(builder, true);
    }

    public FixedBackOffPolicy createBackOffPolicy() {
        // Step/Tasklet 모두 같은 backoff 간격을 쓰도록 공통 정책을 만듦
        FixedBackOffPolicy policy = new FixedBackOffPolicy();
        policy.setBackOffPeriod(backOffMillis);
        return policy;
    }

    private <I, O> FaultTolerantStepBuilder<I, O> applyRetry(
            FaultTolerantStepBuilder<I, O> builder, boolean includeRedis) {
        // Redis step도 reader 쪽 DB timeout 가능성이 있어 QueryTimeoutException은 공통으로 포함
        builder.retry(PessimisticLockingFailureException.class).retry(QueryTimeoutException.class);

        if (includeRedis) {
            builder.retry(RedisConnectionFailureException.class);
        }

        return builder.retryLimit(retryLimit).backOffPolicy(createBackOffPolicy());
    }

    private RetryTemplate createRetryTemplate(Map<Class<? extends Throwable>, Boolean> retryable) {
        // Tasklet은 Step faultTolerant를 쓸 수 없어서 RetryTemplate으로 같은 정책을 재사용
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(createBackOffPolicy());
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(retryLimit, retryable, true));
        return retryTemplate;
    }

    private Map<Class<? extends Throwable>, Boolean> buildRetryableExceptions(
            boolean includeRedis) {
        Map<Class<? extends Throwable>, Boolean> retryable = new LinkedHashMap<>();
        retryable.put(PessimisticLockingFailureException.class, true);
        retryable.put(QueryTimeoutException.class, true);

        if (includeRedis) {
            retryable.put(RedisConnectionFailureException.class, true);
        }

        return retryable;
    }
}
