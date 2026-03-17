package com.template.worker.jobs.usageprecreate.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.global.listener.JobFailureAlertListener;
import com.template.worker.global.listener.JobResultListener;
import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.usageprecreate.listener.MonthlyUsagePrecreateJobListener;
import com.template.worker.jobs.usageprecreate.step.AcquireMonthlyUsagePrecreateLockStepConfig;
import com.template.worker.jobs.usageprecreate.step.PrecreateCustomerQuotaStepConfig;
import com.template.worker.jobs.usageprecreate.step.PrecreateFamilyQuotaStepConfig;
import com.template.worker.jobs.usageprecreate.step.ReleaseMonthlyUsagePrecreateLockStepConfig;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MonthlyUsagePrecreateJobConfig {

    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final JobFailureAlertListener jobFailureAlertListener;
    private final MonthlyUsagePrecreateJobListener jobListener;
    private final AcquireMonthlyUsagePrecreateLockStepConfig acquireLockStepConfig;
    private final PrecreateCustomerQuotaStepConfig precreateCustomerQuotaStepConfig;
    private final PrecreateFamilyQuotaStepConfig precreateFamilyQuotaStepConfig;
    private final ReleaseMonthlyUsagePrecreateLockStepConfig releaseLockStepConfig;

    @Bean
    public Job monthlyUsagePrecreateJob() {
        return new JobBuilder(MonthlyUsagePrecreateJobConstants.JOB_NAME, jobRepository)
                .listener(jobResultListener)
                .listener(jobFailureAlertListener)
                .listener(jobListener)
                .start(acquireLockStepConfig.acquireMonthlyUsagePrecreateLockStep())
                // 락 미획득이면 정상 종료해 중복 실행을 방지
                .on(BatchJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED)
                .end()
                .from(acquireLockStepConfig.acquireMonthlyUsagePrecreateLockStep())
                .on("FAILED")
                .fail()
                .from(acquireLockStepConfig.acquireMonthlyUsagePrecreateLockStep())
                // 락 획득 이후에만 다음 달 quota 선생성 체인을 수행
                .on("*")
                .to(precreateCustomerQuotaStepConfig.precreateCustomerQuotaStep())
                .next(precreateFamilyQuotaStepConfig.precreateFamilyQuotaStep())
                .next(releaseLockStepConfig.releaseMonthlyUsagePrecreateLockStep())
                .end()
                .build();
    }
}
