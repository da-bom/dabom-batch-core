package com.template.worker.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.template.worker.api.dto.BatchJobExecutionResponse;
import com.template.worker.api.dto.RunBatchRequest;
import com.template.worker.api.dto.RunBatchResponse;
import com.template.worker.api.service.BatchAdminService;
import com.template.worker.api.service.BatchJobQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Batch 운영용 관리자 컨트롤러 - 운영자가 수동으로 배치를 실행하거나 외부 스케줄러가 배치를 트리거할 때 사용 - 모든 배치는 jobName + 파라미터 기반으로 실행
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/batch")
public class BatchAdminController {
    private final BatchAdminService service;
    private final BatchJobQueryService queryService;

    @PostMapping("/run")
    public ResponseEntity<RunBatchResponse> run(@RequestBody RunBatchRequest request)
            throws Exception {
        return ResponseEntity.accepted().body(service.run(request));
    }

    @GetMapping("/jobs/{jobExecutionId}")
    public BatchJobExecutionResponse getJobExecution(@PathVariable Long jobExecutionId) {
        return queryService.getJobExecution(jobExecutionId);
    }
}
