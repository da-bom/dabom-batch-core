package com.template.worker.jobs.eventoutbox.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.common.listener.JobResultListener;
import com.template.worker.jobs.eventoutbox.step.PublishEventOutboxStepConfig;
import com.template.worker.jobs.eventoutbox.support.EventOutboxJobConstants;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class EventOutboxPublishJobConfig {

    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final PublishEventOutboxStepConfig publishEventOutboxStepConfig;

    /** event outbox publish 전용 단일 step job을 등록한다. */
    @Bean
    public Job eventOutboxPublishJob() {
        return new JobBuilder(EventOutboxJobConstants.JOB_NAME, jobRepository)
                .listener(jobResultListener)
                .start(publishEventOutboxStepConfig.publishEventOutboxStep())
                .build();
    }
}
