package com.template.worker.jobs.usageprecreate.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.tasklet.PrecreateCustomerQuotaTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class PrecreateCustomerQuotaStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PrecreateCustomerQuotaTasklet tasklet;

    @Bean
    public Step precreateCustomerQuotaStep() {
        return new StepBuilder(
                        MonthlyUsagePrecreateJobConstants.STEP_PRECREATE_CUSTOMER_QUOTA,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
