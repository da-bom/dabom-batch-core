package com.template.worker.jobs.recap.monthly.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.tasklet.MonthlyFamilyRecapLockReleaseTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ReleaseMonthlyFamilyRecapLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyFamilyRecapLockReleaseTasklet tasklet;

    @Bean
    public Step releaseMonthlyFamilyRecapLockStep() {
        // 마지막에 락 해제 태스크릿을 실행
        return new StepBuilder(
                        MonthlyFamilyRecapJobConstants.STEP_RELEASE_MONTHLY_RECAP_LOCK,
                        jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
