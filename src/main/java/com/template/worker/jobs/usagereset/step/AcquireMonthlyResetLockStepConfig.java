package com.template.worker.jobs.usagereset.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.tasklet.MonthlyResetLockTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AcquireMonthlyResetLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyResetLockTasklet tasklet;

    @Bean
    public Step acquireMonthlyResetLockStep() {
        // 락 획득 Tasklet을 Step으로 연결
        return new StepBuilder(
                        MonthlyUsageResetJobConstants.STEP_ACQUIRE_MONTHLY_RESET_LOCK,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
