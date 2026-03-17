package com.template.worker.jobs.reconciliation.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;

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

import com.template.worker.jobs.common.support.BatchLockManager;

@ExtendWith(MockitoExtension.class)
class DbRedisReconciliationJobListenerTest {

    @Mock private BatchLockManager batchLockManager;

    @InjectMocks private DbRedisReconciliationJobListener jobListener;

    @Test
    @DisplayName("afterJob - 최종 락 정리 예외가 나도 후속 리스너를 막지 않도록 예외를 삼킨다")
    void afterJob_swallowsCleanupException() {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "db-redis-reconciliation-job"),
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.getExecutionContext().putString("targetMonth", "2026-03-01");
        jobExecution
                .getExecutionContext()
                .putString("lockKey", "batch:lock:reconciliation:2026-03-01");
        jobExecution.getExecutionContext().putString("lockOwner", "owner");

        doThrow(new QueryTimeoutException("Redis command timed out"))
                .when(batchLockManager)
                .releaseIfOwner("batch:lock:reconciliation:2026-03-01", "owner");

        assertThatCode(() -> jobListener.afterJob(jobExecution)).doesNotThrowAnyException();
    }
}
