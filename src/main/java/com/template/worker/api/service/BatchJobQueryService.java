package com.template.worker.api.service;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.template.worker.api.dto.BatchJobExecutionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BatchJobQueryService {
    private final JobExplorer jobExplorer;

    public BatchJobExecutionResponse getJobExecution(Long jobExecutionId) {
        JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);
        if (jobExecution == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Job execution not found: " + jobExecutionId);
        }

        boolean executionFinished = jobExecution.getEndTime() != null;
        ExitStatus exitStatus = jobExecution.getExitStatus();
        String exitCode = null;
        String exitDescription = null;
        if (executionFinished) {
            exitCode = exitStatus.getExitCode();
            if (!exitStatus.getExitDescription().isBlank()) {
                exitDescription = exitStatus.getExitDescription();
            }
        }

        return new BatchJobExecutionResponse(
                jobExecution.getId(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus().name(),
                jobExecution.getCreateTime(),
                jobExecution.getStartTime(),
                jobExecution.getEndTime(),
                exitCode,
                exitDescription);
    }
}
