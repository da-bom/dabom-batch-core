package com.template.worker.jobs.usageprecreate.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.tasklet.PrecreateFamilyQuotaTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class PrecreateFamilyQuotaStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PrecreateFamilyQuotaTasklet tasklet;

    @Bean
    public Step precreateFamilyQuotaStep() {
        return new StepBuilder(
                        MonthlyUsagePrecreateJobConstants.STEP_PRECREATE_FAMILY_QUOTA,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
