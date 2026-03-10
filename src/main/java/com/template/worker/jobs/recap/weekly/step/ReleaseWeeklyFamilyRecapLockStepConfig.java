package com.template.worker.jobs.recap.weekly.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.tasklet.WeeklyFamilyRecapLockReleaseTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ReleaseWeeklyFamilyRecapLockStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WeeklyFamilyRecapLockReleaseTasklet tasklet;

    @Bean
    public Step releaseWeeklyFamilyRecapLockStep() {
        // 마지막에 락 해제 태스크릿을 실행
        return new StepBuilder(
                        WeeklyFamilyRecapJobConstants.STEP_RELEASE_WEEKLY_RECAP_LOCK, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
