package com.template.worker.jobs.recap.weekly.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.tasklet.WeeklyFamilyRecapLockTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AcquireWeeklyFamilyRecapLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WeeklyFamilyRecapLockTasklet tasklet;

    @Bean
    public Step acquireWeeklyFamilyRecapLockStep() {
        // 락 획득 태스크릿을 스텝으로 구성
        return new StepBuilder(
                        WeeklyFamilyRecapJobConstants.STEP_ACQUIRE_WEEKLY_RECAP_LOCK, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
