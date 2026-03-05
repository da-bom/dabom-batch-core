package com.template.worker.jobs.usagereset.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.tasklet.MonthlyResetLockReleaseTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ReleaseMonthlyResetLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyResetLockReleaseTasklet tasklet;

    @Bean
    public Step releaseMonthlyResetLockStep() {
        // 마지막 락 해제 Tasklet Step 정의
        return new StepBuilder(
                        MonthlyUsageResetJobConstants.STEP_RELEASE_MONTHLY_RESET_LOCK,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
