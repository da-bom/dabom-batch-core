package com.template.worker.jobs.usageoutbox.scheduler;

import java.util.Collections;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.template.worker.common.launcher.BatchJobLauncher;
import com.template.worker.jobs.usageoutbox.support.UsageEventOutboxJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "batch.schedules.usage-event-outbox.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class UsageEventOutboxPublishScheduler {

    private final BatchJobLauncher launcher;

    @Scheduled(
            fixedDelayString = "${batch.schedules.usage-event-outbox.fixed-delay:10000}",
            initialDelayString = "${batch.schedules.usage-event-outbox.initial-delay:5000}",
            zone = UsageEventOutboxJobConstants.KST_ZONE_ID_NAME)
    public void publishUsageEventOutboxes() {
        try {
            launcher.run(UsageEventOutboxJobConstants.JOB_NAME, Collections.emptyMap());
        } catch (Exception exception) {
            log.error("Failed to run usage event outbox publish job by scheduler.", exception);
        }
    }
}
