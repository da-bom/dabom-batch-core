package com.template.worker.jobs.usagereset.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.tasklet.FamilyMonthResetTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ResetFamilyMonthStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FamilyMonthResetTasklet tasklet;

    @Bean
    public Step resetFamilyMonthStep() {
        // DB 리셋 Tasklet Step 정의
        return new StepBuilder(MonthlyUsageResetJobConstants.STEP_RESET_FAMILY_MONTH, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
