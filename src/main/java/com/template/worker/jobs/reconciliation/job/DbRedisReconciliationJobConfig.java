package com.template.worker.jobs.reconciliation.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.global.listener.JobFailureAlertListener;
import com.template.worker.global.listener.JobResultListener;
import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.reconciliation.listener.DbRedisReconciliationJobListener;
import com.template.worker.jobs.reconciliation.step.AcquireReconciliationLockStepConfig;
import com.template.worker.jobs.reconciliation.step.InvalidateCustomerMonthlyUsageStepConfig;
import com.template.worker.jobs.reconciliation.step.InvalidateFamilyInfoAndRemainingStepConfig;
import com.template.worker.jobs.reconciliation.step.ReleaseReconciliationLockStepConfig;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class DbRedisReconciliationJobConfig {

    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final JobFailureAlertListener jobFailureAlertListener;
    private final DbRedisReconciliationJobListener dbRedisReconciliationJobListener;
    private final AcquireReconciliationLockStepConfig acquireReconciliationLockStepConfig;
    private final InvalidateFamilyInfoAndRemainingStepConfig
            invalidateFamilyInfoAndRemainingStepConfig;
    private final InvalidateCustomerMonthlyUsageStepConfig invalidateCustomerMonthlyUsageStepConfig;
    private final ReleaseReconciliationLockStepConfig releaseReconciliationLockStepConfig;

    @Bean
    public Job dbRedisReconciliationJob() {
        return new JobBuilder(DbRedisReconciliationJobConstants.JOB_NAME, jobRepository)
                .listener(jobResultListener)
                .listener(jobFailureAlertListener)
                .listener(dbRedisReconciliationJobListener)
                .start(acquireReconciliationLockStepConfig.acquireReconciliationLockStep())
                // 락 미획득이면 정상 종료해 중복 실행을 방지함
                .on(BatchJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED)
                .end()
                .from(acquireReconciliationLockStepConfig.acquireReconciliationLockStep())
                .on("FAILED")
                .fail()
                .from(acquireReconciliationLockStepConfig.acquireReconciliationLockStep())
                // 락 획득 이후에만 Redis 키 무효화 스텝 체인을 수행함
                .on("*")
                .to(
                        invalidateFamilyInfoAndRemainingStepConfig
                                .invalidateFamilyInfoAndRemainingStep())
                .next(invalidateCustomerMonthlyUsageStepConfig.invalidateCustomerMonthlyUsageStep())
                .next(releaseReconciliationLockStepConfig.releaseReconciliationLockStep())
                .end()
                .build();
    }
}
