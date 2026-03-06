package com.template.worker.jobs.reconciliation.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.tasklet.ReconciliationLockReleaseTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ReleaseReconciliationLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReconciliationLockReleaseTasklet tasklet;

    @Bean
    public Step releaseReconciliationLockStep() {
        // 마지막 락 해제 Tasklet Step 정의
        return new StepBuilder(
                        DbRedisReconciliationJobConstants.STEP_RELEASE_RECONCILIATION_LOCK,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
