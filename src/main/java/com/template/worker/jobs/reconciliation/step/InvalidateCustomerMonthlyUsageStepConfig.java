package com.template.worker.jobs.reconciliation.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.global.retry.BatchRetrySupport;
import com.template.worker.jobs.reconciliation.model.FamilyMemberReconciliationTarget;
import com.template.worker.jobs.reconciliation.reader.ReconciliationFamilyMemberReader;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;
import com.template.worker.jobs.reconciliation.writer.ReconciliationCustomerMonthlyUsageInvalidationWriter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class InvalidateCustomerMonthlyUsageStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReconciliationFamilyMemberReader reader;
    private final ReconciliationCustomerMonthlyUsageInvalidationWriter writer;
    private final DbRedisReconciliationProperties properties;
    private final BatchRetrySupport batchRetrySupport;

    @Bean
    public Step invalidateCustomerMonthlyUsageStep() {
        // family_member reader + monthly usage invalidation writer의 Chunk Step 구성
        return batchRetrySupport
                .applyRedisRetry(
                        new StepBuilder(
                                        DbRedisReconciliationJobConstants
                                                .STEP_INVALIDATE_CUSTOMER_MONTHLY_USAGE,
                                        jobRepository)
                                .<FamilyMemberReconciliationTarget,
                                        FamilyMemberReconciliationTarget>
                                        chunk(properties.getRedisChunkSize(), transactionManager)
                                .reader(reader)
                                .writer(writer)
                                .faultTolerant())
                .build();
    }
}
