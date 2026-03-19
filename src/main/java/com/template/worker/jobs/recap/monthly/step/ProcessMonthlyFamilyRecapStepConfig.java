package com.template.worker.jobs.recap.monthly.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.common.retry.BatchRetrySupport;
import com.template.worker.jobs.recap.monthly.reader.MonthlyFamilyRecapFamilyReader;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapProperties;
import com.template.worker.jobs.recap.monthly.writer.MonthlyFamilyRecapUpsertWriter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ProcessMonthlyFamilyRecapStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyFamilyRecapFamilyReader reader;
    private final MonthlyFamilyRecapUpsertWriter writer;
    private final MonthlyFamilyRecapProperties properties;
    private final BatchRetrySupport batchRetrySupport;

    @Bean
    public Step processMonthlyFamilyRecapStep() {
        // 활성 가족을 읽어 집계 후 즉시 업서트하는 청크 스텝
        return batchRetrySupport
                .applyDbRetry(
                        new StepBuilder(
                                        MonthlyFamilyRecapJobConstants.STEP_PROCESS_MONTHLY_RECAP,
                                        jobRepository)
                                .<Long, Long>chunk(
                                        properties.getChunkSize(), transactionManager)
                                .reader(reader)
                                .writer(writer)
                                .listener(writer)
                                .faultTolerant())
                .build();
    }
}
