package com.template.worker.jobs.recap.weekly.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.global.retry.BatchRetrySupport;
import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapRow;
import com.template.worker.jobs.recap.weekly.processor.WeeklyFamilyRecapProcessor;
import com.template.worker.jobs.recap.weekly.reader.WeeklyFamilyRecapFamilyReader;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapProperties;
import com.template.worker.jobs.recap.weekly.writer.WeeklyFamilyRecapUpsertWriter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ProcessWeeklyFamilyRecapStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WeeklyFamilyRecapFamilyReader reader;
    private final WeeklyFamilyRecapProcessor processor;
    private final WeeklyFamilyRecapUpsertWriter writer;
    private final WeeklyFamilyRecapProperties properties;
    private final BatchRetrySupport batchRetrySupport;

    @Bean
    public Step processWeeklyFamilyRecapStep() {
        // 활성 가족을 읽어 집계 후 즉시 업서트하는 청크 스텝
        return batchRetrySupport
                .applyDbRetry(
                        new StepBuilder(
                                        WeeklyFamilyRecapJobConstants.STEP_PROCESS_WEEKLY_RECAP,
                                        jobRepository)
                                .<Long, WeeklyFamilyRecapRow>chunk(
                                        properties.getChunkSize(), transactionManager)
                                .reader(reader)
                                .processor(processor)
                                .writer(writer)
                                .faultTolerant())
                .build();
    }
}
