package com.template.worker.jobs.eventoutbox.model;

public record EventOutboxRow(long id, String eventId, String payloadJson, int retryCount) {}
