package com.template.worker.jobs.reconciliation.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.reconciliation.reader.ReconciliationFamilyReader;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;
import com.template.worker.jobs.reconciliation.writer.ReconciliationFamilyKeyInvalidationWriter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class InvalidateFamilyInfoAndRemainingStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReconciliationFamilyReader reader;
    private final ReconciliationFamilyKeyInvalidationWriter writer;
    private final DbRedisReconciliationProperties properties;

    @Bean
    public Step invalidateFamilyInfoAndRemainingStep() {
        // family id reader + info/remaining invalidation writer의 Chunk Step 구성
        return new StepBuilder(
                        DbRedisReconciliationJobConstants.STEP_INVALIDATE_FAMILY_INFO_AND_REMAINING,
                        jobRepository)
                .<Long, Long>chunk(properties.getRedisChunkSize(), transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }
}
