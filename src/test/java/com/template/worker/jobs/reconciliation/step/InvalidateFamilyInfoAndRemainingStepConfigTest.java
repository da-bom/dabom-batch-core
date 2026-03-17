package com.template.worker.jobs.reconciliation.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.common.retry.BatchRetrySupport;
import com.template.worker.jobs.reconciliation.reader.ReconciliationFamilyReader;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;
import com.template.worker.jobs.reconciliation.writer.ReconciliationFamilyKeyInvalidationWriter;

class InvalidateFamilyInfoAndRemainingStepConfigTest {

    @Test
    @DisplayName("invalidateFamilyInfoAndRemainingStep - Redis 오류가 두 번 나도 세 번째에 성공한다")
    void invalidateFamilyInfoAndRemainingStep_retriesRedisFailuresThenCompletes() throws Exception {
        JobRepository jobRepository = mock(JobRepository.class);
        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();
        ReconciliationFamilyReader reader = mock(ReconciliationFamilyReader.class);
        ReconciliationFamilyKeyInvalidationWriter writer =
                mock(ReconciliationFamilyKeyInvalidationWriter.class);
        DbRedisReconciliationProperties properties = new DbRedisReconciliationProperties();
        properties.setRedisChunkSize(2);

        InvalidateFamilyInfoAndRemainingStepConfig stepConfig =
                new InvalidateFamilyInfoAndRemainingStepConfig(
                        jobRepository,
                        transactionManager,
                        reader,
                        writer,
                        properties,
                        new BatchRetrySupport(3, 0L));

        when(reader.read()).thenReturn(10L, 11L, null);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new RedisConnectionFailureException("redis");
                            }
                            return null;
                        })
                .when(writer)
                .write(any());

        Step step = stepConfig.invalidateFamilyInfoAndRemainingStep();
        StepExecution stepExecution = createStepExecution("invalidate-family-info-step");

        step.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("invalidateFamilyInfoAndRemainingStep - Redis 오류가 한도를 넘으면 FAILED 처리된다")
    void invalidateFamilyInfoAndRemainingStep_failsAfterRetryLimitExceeded() throws Exception {
        JobRepository jobRepository = mock(JobRepository.class);
        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();
        ReconciliationFamilyReader reader = mock(ReconciliationFamilyReader.class);
        ReconciliationFamilyKeyInvalidationWriter writer =
                mock(ReconciliationFamilyKeyInvalidationWriter.class);
        DbRedisReconciliationProperties properties = new DbRedisReconciliationProperties();
        properties.setRedisChunkSize(2);

        InvalidateFamilyInfoAndRemainingStepConfig stepConfig =
                new InvalidateFamilyInfoAndRemainingStepConfig(
                        jobRepository,
                        transactionManager,
                        reader,
                        writer,
                        properties,
                        new BatchRetrySupport(3, 0L));

        when(reader.read()).thenReturn(10L, null);
        doThrow(new RedisConnectionFailureException("redis")).when(writer).write(any());

        Step step = stepConfig.invalidateFamilyInfoAndRemainingStep();
        StepExecution stepExecution = createStepExecution("invalidate-family-info-step");

        step.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    private StepExecution createStepExecution(String stepName) {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "dbRedisReconciliationJob"),
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        return new StepExecution(stepName, jobExecution);
    }
}
