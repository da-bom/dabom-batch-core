package com.template.worker.jobs.recap.weekly.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.tasklet.WeeklyFamilyRecapUpsertCompletionTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class UpsertWeeklyFamilyRecapStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WeeklyFamilyRecapUpsertCompletionTasklet tasklet;

    @Bean
    public Step upsertWeeklyFamilyRecapStep() {
        // 집계 스텝 이후 파이프라인 단계를 명시하는 마무리 스텝
        return new StepBuilder(
                        WeeklyFamilyRecapJobConstants.STEP_UPSERT_WEEKLY_RECAP, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
