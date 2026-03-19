package com.template.worker.api.dto;

public record RunBatchResponse(
        Long jobExecutionId, String jobName, String status, String message) {}
