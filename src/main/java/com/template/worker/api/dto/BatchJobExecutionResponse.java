package com.template.worker.api.dto;

import java.time.LocalDateTime;

public record BatchJobExecutionResponse(
        Long jobExecutionId,
        String jobName,
        String status,
        LocalDateTime createTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String exitCode,
        String exitDescription) {}
