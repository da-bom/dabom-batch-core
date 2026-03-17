package com.template.worker.jobs.usageoutbox.tasklet;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.usageoutbox.service.UsageEventOutboxPublishService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsageEventOutboxPublishTasklet implements Tasklet {

    private final UsageEventOutboxPublishService publishService;

    /** 한 번의 job execution에서 publish 가능한 outbox를 처리한다. */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int processedCount = publishService.publishPendingOutboxes();
        if (processedCount > 0) {
            log.info(
                    "Processed usage event outbox rows in batch job. processedCount={}",
                    processedCount);
        }
        return RepeatStatus.FINISHED;
    }
}
