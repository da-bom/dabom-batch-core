package com.template.worker.jobs.eventoutbox.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.eventoutbox.support.EventOutboxJobConstants;
import com.template.worker.jobs.eventoutbox.tasklet.EventOutboxPublishTasklet;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class PublishEventOutboxStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EventOutboxPublishTasklet tasklet;

    /** usage outbox publish tasklet을 감싸는 단일 step을 구성한다. */
    @Bean
    public Step publishUsageEventOutboxStep() {
        return new StepBuilder(
                        EventOutboxJobConstants.STEP_PUBLISH_USAGE_EVENT_OUTBOX, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
