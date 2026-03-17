package com.template.worker.jobs.usageoutbox.model;

public record UsageEventOutboxRow(long id, String eventId, String payloadJson, int retryCount) {}
