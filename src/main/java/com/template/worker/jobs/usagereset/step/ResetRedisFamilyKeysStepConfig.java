package com.template.worker.jobs.usagereset.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.common.retry.BatchRetrySupport;
import com.template.worker.jobs.usagereset.reader.ActiveFamilyReader;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetProperties;
import com.template.worker.jobs.usagereset.writer.FamilyRedisResetWriter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ResetRedisFamilyKeysStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ActiveFamilyReader reader;
    private final FamilyRedisResetWriter writer;
    private final MonthlyUsageResetProperties properties;
    private final BatchRetrySupport batchRetrySupport;

    @Bean
    public Step resetRedisFamilyKeysStep() {
        // family id reader + redis delete writer의 Chunk Step 구성
        return batchRetrySupport
                .applyRedisRetry(
                        new StepBuilder(
                                        MonthlyUsageResetJobConstants.STEP_RESET_REDIS_FAMILY_KEYS,
                                        jobRepository)
                                .<Long, Long>chunk(
                                        properties.getRedisChunkSize(), transactionManager)
                                .reader(reader)
                                .writer(writer)
                                .faultTolerant())
                .build();
    }
}
