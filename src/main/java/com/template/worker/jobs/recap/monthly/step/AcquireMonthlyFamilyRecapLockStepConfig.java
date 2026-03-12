package com.template.worker.jobs.recap.monthly.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.tasklet.MonthlyFamilyRecapLockTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AcquireMonthlyFamilyRecapLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyFamilyRecapLockTasklet tasklet;

    @Bean
    public Step acquireMonthlyFamilyRecapLockStep() {
        // 락 획득 태스크릿을 스텝으로 구성
        return new StepBuilder(
                        MonthlyFamilyRecapJobConstants.STEP_ACQUIRE_MONTHLY_RECAP_LOCK,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
