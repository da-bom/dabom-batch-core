package com.template.worker.jobs.eventoutbox.scheduler;

import java.util.Collections;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.template.worker.common.launcher.BatchJobLauncher;
import com.template.worker.jobs.eventoutbox.support.EventOutboxJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "batch.schedules.event-outbox.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class EventOutboxPublishScheduler {

    private final BatchJobLauncher launcher;

    @Scheduled(
            fixedDelayString = "${batch.schedules.event-outbox.fixed-delay:10000}",
            initialDelayString = "${batch.schedules.event-outbox.initial-delay:5000}",
            zone = EventOutboxJobConstants.KST_ZONE_ID_NAME)
    public void publishEventOutboxes() {
        try {
            launcher.run(EventOutboxJobConstants.JOB_NAME, Collections.emptyMap());
        } catch (Exception exception) {
            log.error("Failed to run event outbox publish job by scheduler.", exception);
        }
    }
}
