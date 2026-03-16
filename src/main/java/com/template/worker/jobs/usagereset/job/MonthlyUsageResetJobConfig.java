package com.template.worker.jobs.usagereset.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.global.listener.JobFailureAlertListener;
import com.template.worker.global.listener.JobResultListener;
import com.template.worker.jobs.usagereset.listener.MonthlyUsageResetJobListener;
import com.template.worker.jobs.usagereset.step.AcquireMonthlyResetLockStepConfig;
import com.template.worker.jobs.usagereset.step.ReleaseMonthlyResetLockStepConfig;
import com.template.worker.jobs.usagereset.step.ResetRedisCustomerMonthlyUsageStepConfig;
import com.template.worker.jobs.usagereset.step.ResetRedisFamilyKeysStepConfig;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MonthlyUsageResetJobConfig {
    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final JobFailureAlertListener jobFailureAlertListener;
    private final MonthlyUsageResetJobListener monthlyUsageResetJobListener;
    private final AcquireMonthlyResetLockStepConfig acquireMonthlyResetLockStepConfig;
    private final ResetRedisFamilyKeysStepConfig resetRedisFamilyKeysStepConfig;
    private final ResetRedisCustomerMonthlyUsageStepConfig resetRedisCustomerMonthlyUsageStepConfig;
    private final ReleaseMonthlyResetLockStepConfig releaseMonthlyResetLockStepConfig;

    @Bean
    public Job monthlyUsageResetJob() {
        return new JobBuilder(MonthlyUsageResetJobConstants.JOB_NAME, jobRepository)
                .listener(jobResultListener)
                .listener(jobFailureAlertListener)
                .listener(monthlyUsageResetJobListener)
                .start(acquireMonthlyResetLockStepConfig.acquireMonthlyResetLockStep())
                // 락 미획득이면 정상 종료해 중복 실행을 방지함
                .on(MonthlyUsageResetJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED)
                .end()
                .from(acquireMonthlyResetLockStepConfig.acquireMonthlyResetLockStep())
                .on("FAILED")
                .fail()
                .from(acquireMonthlyResetLockStepConfig.acquireMonthlyResetLockStep())
                // 락 획득 이후에만 전월 Redis 정리 스텝 체인을 수행
                .on("*")
                .to(resetRedisFamilyKeysStepConfig.resetRedisFamilyKeysStep())
                .next(resetRedisCustomerMonthlyUsageStepConfig.resetRedisCustomerMonthlyUsageStep())
                .next(releaseMonthlyResetLockStepConfig.releaseMonthlyResetLockStep())
                .end()
                .build();
    }
}
