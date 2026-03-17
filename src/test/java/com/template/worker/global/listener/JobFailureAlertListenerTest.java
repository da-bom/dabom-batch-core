package com.template.worker.global.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.retry.BatchRetrySupport;

import io.lettuce.core.RedisCommandTimeoutException;

@ExtendWith(MockitoExtension.class)
class JobFailureAlertListenerTest {

    @Mock private BatchAlertService batchAlertService;
    @Mock private BatchRetrySupport batchRetrySupport;

    @InjectMocks private JobFailureAlertListener jobFailureAlertListener;

    @Test
    @DisplayName("afterJob - FAILED 상태면 핵심 파라미터와 함께 Slack 알람을 보낸다")
    void afterJob_sendsAlertWhenJobFailed() {
        JobInstance jobInstance = new JobInstance(1L, "monthlyUsageResetJob");
        JobExecution jobExecution =
                new JobExecution(
                        jobInstance,
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(
                new IllegalStateException(
                        "Retry exhausted after last attempt in recovery path, but exception is not"
                                + " skippable.",
                        new RedisConnectionFailureException("Unable to connect to Redis")));
        org.mockito.Mockito.when(batchRetrySupport.getRetryLimit()).thenReturn(3);

        jobFailureAlertListener.afterJob(jobExecution);

        verify(batchAlertService)
                .sendJobFailureAlert(
                        eq("monthlyUsageResetJob"),
                        isNull(),
                        eq("targetMonth=2026-03-01"),
                        eq("Redis 연결 실패로 재시도 3회 후 최종 실패"),
                        eq("RedisConnectionFailureException: Unable to connect to Redis"));
    }

    @Test
    @DisplayName("afterJob - COMPLETED 상태면 Slack 알람을 보내지 않는다")
    void afterJob_doesNotSendAlertWhenJobCompleted() {
        JobInstance jobInstance = new JobInstance(1L, "monthlyUsageResetJob");
        JobExecution jobExecution =
                new JobExecution(
                        jobInstance,
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        jobExecution.setStatus(BatchStatus.COMPLETED);

        jobFailureAlertListener.afterJob(jobExecution);

        verify(batchAlertService, never())
                .sendJobFailureAlert(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("afterJob - Redis command timed out 은 Redis 명령 타임아웃으로 요약한다")
    void afterJob_summarizesRedisTimeoutAsRedisFailure() {
        JobInstance jobInstance = new JobInstance(2L, "db-redis-reconciliation-job");
        JobExecution jobExecution =
                new JobExecution(
                        jobInstance,
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(
                new IllegalStateException(
                        "Retry exhausted after last attempt in recovery path, but exception is not"
                                + " skippable.",
                        new QueryTimeoutException("Redis command timed out")));
        org.mockito.Mockito.when(batchRetrySupport.getRetryLimit()).thenReturn(3);

        jobFailureAlertListener.afterJob(jobExecution);

        verify(batchAlertService)
                .sendJobFailureAlert(
                        eq("db-redis-reconciliation-job"),
                        isNull(),
                        eq("targetMonth=2026-03-01"),
                        eq("Redis 명령 타임아웃으로 재시도 3회 후 최종 실패"),
                        eq("QueryTimeoutException: Redis command timed out"));
    }

    @Test
    @DisplayName("afterJob - root cause 가 RedisCommandTimeoutException 이어도 Redis 명령 타임아웃으로 요약한다")
    void afterJob_summarizesDirectRedisCommandTimeoutAsRedisFailure() {
        JobInstance jobInstance = new JobInstance(3L, "db-redis-reconciliation-job");
        JobExecution jobExecution =
                new JobExecution(
                        jobInstance,
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(
                new IllegalStateException(
                        "Retry exhausted after last attempt in recovery path, but exception is not"
                                + " skippable.",
                        new QueryTimeoutException(
                                "Redis command timed out",
                                new RedisCommandTimeoutException(
                                        "Command timed out after 1 minute(s)"))));
        org.mockito.Mockito.when(batchRetrySupport.getRetryLimit()).thenReturn(3);

        jobFailureAlertListener.afterJob(jobExecution);

        verify(batchAlertService)
                .sendJobFailureAlert(
                        eq("db-redis-reconciliation-job"),
                        isNull(),
                        eq("targetMonth=2026-03-01"),
                        eq("Redis 명령 타임아웃으로 재시도 3회 후 최종 실패"),
                        eq("RedisCommandTimeoutException: Command timed out after 1 minute(s)"));
    }
}
