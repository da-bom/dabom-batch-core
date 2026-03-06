package com.template.worker.jobs.reconciliation.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.tasklet.ReconciliationLockTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AcquireReconciliationLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReconciliationLockTasklet tasklet;

    @Bean
    public Step acquireReconciliationLockStep() {
        // 락 획득 Tasklet을 Step으로 연결
        return new StepBuilder(
                        DbRedisReconciliationJobConstants.STEP_ACQUIRE_RECONCILIATION_LOCK,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
