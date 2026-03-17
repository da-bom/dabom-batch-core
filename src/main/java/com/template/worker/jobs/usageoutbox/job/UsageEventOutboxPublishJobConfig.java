package com.template.worker.jobs.usageoutbox.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.common.listener.JobResultListener;
import com.template.worker.jobs.usageoutbox.step.PublishUsageEventOutboxStepConfig;
import com.template.worker.jobs.usageoutbox.support.UsageEventOutboxJobConstants;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class UsageEventOutboxPublishJobConfig {

    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final PublishUsageEventOutboxStepConfig publishUsageEventOutboxStepConfig;

    /** usage outbox publish 전용 단일 step job을 등록한다. */
    @Bean
    public Job usageEventOutboxPublishJob() {
        return new JobBuilder(UsageEventOutboxJobConstants.JOB_NAME, jobRepository)
                .listener(jobResultListener)
                .start(publishUsageEventOutboxStepConfig.publishUsageEventOutboxStep())
                .build();
    }
}
