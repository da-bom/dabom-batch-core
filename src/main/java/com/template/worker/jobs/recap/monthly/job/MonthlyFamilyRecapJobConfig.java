package com.template.worker.jobs.recap.monthly.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.global.listener.JobFailureAlertListener;
import com.template.worker.global.listener.JobResultListener;
import com.template.worker.jobs.recap.monthly.listener.MonthlyFamilyRecapJobListener;
import com.template.worker.jobs.recap.monthly.step.AcquireMonthlyFamilyRecapLockStepConfig;
import com.template.worker.jobs.recap.monthly.step.ProcessMonthlyFamilyRecapStepConfig;
import com.template.worker.jobs.recap.monthly.step.ReleaseMonthlyFamilyRecapLockStepConfig;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MonthlyFamilyRecapJobConfig {

    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final JobFailureAlertListener jobFailureAlertListener;
    private final MonthlyFamilyRecapJobListener monthlyFamilyRecapJobListener;
    private final AcquireMonthlyFamilyRecapLockStepConfig acquireMonthlyFamilyRecapLockStepConfig;
    private final ProcessMonthlyFamilyRecapStepConfig processMonthlyFamilyRecapStepConfig;
    private final ReleaseMonthlyFamilyRecapLockStepConfig releaseMonthlyFamilyRecapLockStepConfig;

    @Bean
    public Job monthlyFamilyRecapJob() {
        // 락 획득 실패면 정상 종료하고 락 획득 성공 시 집계 체인을 실행
        return new JobBuilder(MonthlyFamilyRecapJobConstants.JOB_NAME, jobRepository)
                .listener(jobResultListener)
                .listener(jobFailureAlertListener)
                .listener(monthlyFamilyRecapJobListener)
                .start(acquireMonthlyFamilyRecapLockStepConfig.acquireMonthlyFamilyRecapLockStep())
                .on(MonthlyFamilyRecapJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED)
                .end()
                .from(acquireMonthlyFamilyRecapLockStepConfig.acquireMonthlyFamilyRecapLockStep())
                // 락 스텝 실패는 잡 실패로 명확히 전파
                .on("FAILED")
                .fail()
                .from(acquireMonthlyFamilyRecapLockStepConfig.acquireMonthlyFamilyRecapLockStep())
                // 락 획득 성공 경로에서만 집계/업서트를 수행
                .on("*")
                .to(processMonthlyFamilyRecapStepConfig.processMonthlyFamilyRecapStep())
                .next(releaseMonthlyFamilyRecapLockStepConfig.releaseMonthlyFamilyRecapLockStep())
                .end()
                .build();
    }
}
