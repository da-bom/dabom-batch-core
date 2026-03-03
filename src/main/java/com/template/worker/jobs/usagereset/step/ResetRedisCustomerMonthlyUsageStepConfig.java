package com.template.worker.jobs.usagereset.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.usagereset.model.FamilyMemberUsageResetTarget;
import com.template.worker.jobs.usagereset.reader.ActiveFamilyMemberReader;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetProperties;
import com.template.worker.jobs.usagereset.writer.CustomerMonthlyUsageResetWriter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ResetRedisCustomerMonthlyUsageStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ActiveFamilyMemberReader reader;
    private final CustomerMonthlyUsageResetWriter writer;
    private final MonthlyUsageResetProperties properties;

    @Bean
    public Step resetRedisCustomerMonthlyUsageStep() {
        // family_member reader + monthly usage reset writer의 Chunk Step 구성
        return new StepBuilder(
                        MonthlyUsageResetJobConstants.STEP_RESET_REDIS_CUSTOMER_MONTHLY_USAGE,
                        jobRepository)
                .<FamilyMemberUsageResetTarget, FamilyMemberUsageResetTarget>chunk(
                        properties.getRedisChunkSize(), transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }
}
