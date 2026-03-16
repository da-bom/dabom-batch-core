package com.template.worker.jobs.usageprecreate.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.tasklet.MonthlyUsagePrecreateLockTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AcquireMonthlyUsagePrecreateLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyUsagePrecreateLockTasklet tasklet;

    @Bean
    public Step acquireMonthlyUsagePrecreateLockStep() {
        return new StepBuilder(
                        MonthlyUsagePrecreateJobConstants.STEP_ACQUIRE_MONTHLY_USAGE_PRECREATE_LOCK,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
