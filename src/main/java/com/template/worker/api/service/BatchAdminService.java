package com.template.worker.api.service;

import org.springframework.batch.core.JobExecution;
import org.springframework.stereotype.Service;

import com.template.worker.api.dto.RunBatchRequest;
import com.template.worker.api.dto.RunBatchResponse;
import com.template.worker.common.launcher.BatchJobLauncher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Batch 실행을 담당하는 서비스 레이어 - 실제 배치 실행은 BatchJobLauncher에 위임한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAdminService {
    private static final String ACCEPTED_MESSAGE = "Batch job accepted";

    private final BatchJobLauncher launcher;

    public RunBatchResponse run(RunBatchRequest request) throws Exception {
        JobExecution jobExecution = launcher.runAsync(request.getJobName(), request.getParams());

        log.info(
                "Manual batch job accepted. jobName={}, params={}, jobExecutionId={}, status={}",
                request.getJobName(),
                request.getParams(),
                jobExecution.getId(),
                jobExecution.getStatus());

        return new RunBatchResponse(
                jobExecution.getId(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus().name(),
                ACCEPTED_MESSAGE);
    }
}
