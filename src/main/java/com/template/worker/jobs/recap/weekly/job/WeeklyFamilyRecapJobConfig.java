package com.template.worker.jobs.recap.weekly.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.global.listener.JobResultListener;
import com.template.worker.jobs.recap.weekly.listener.WeeklyFamilyRecapJobListener;
import com.template.worker.jobs.recap.weekly.step.AcquireWeeklyFamilyRecapLockStepConfig;
import com.template.worker.jobs.recap.weekly.step.ProcessWeeklyFamilyRecapStepConfig;
import com.template.worker.jobs.recap.weekly.step.ReleaseWeeklyFamilyRecapLockStepConfig;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WeeklyFamilyRecapJobConfig {

    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final WeeklyFamilyRecapJobListener weeklyFamilyRecapJobListener;
    private final AcquireWeeklyFamilyRecapLockStepConfig acquireWeeklyFamilyRecapLockStepConfig;
    private final ProcessWeeklyFamilyRecapStepConfig processWeeklyFamilyRecapStepConfig;
    private final ReleaseWeeklyFamilyRecapLockStepConfig releaseWeeklyFamilyRecapLockStepConfig;

    @Bean
    public Job weeklyFamilyRecapJob() {
        // 락 획득 실패면 정상 종료하고 락 획득 성공 시 집계 체인을 실행
        return new JobBuilder(WeeklyFamilyRecapJobConstants.JOB_NAME, jobRepository)
                .listener(jobResultListener)
                .listener(weeklyFamilyRecapJobListener)
                .start(acquireWeeklyFamilyRecapLockStepConfig.acquireWeeklyFamilyRecapLockStep())
                .on(WeeklyFamilyRecapJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED)
                .end()
                .from(acquireWeeklyFamilyRecapLockStepConfig.acquireWeeklyFamilyRecapLockStep())
                .on("FAILED")
                .fail()
                .from(acquireWeeklyFamilyRecapLockStepConfig.acquireWeeklyFamilyRecapLockStep())
                .on("*")
                .to(processWeeklyFamilyRecapStepConfig.processWeeklyFamilyRecapStep())
                .next(releaseWeeklyFamilyRecapLockStepConfig.releaseWeeklyFamilyRecapLockStep())
                .end()
                .build();
    }
}
