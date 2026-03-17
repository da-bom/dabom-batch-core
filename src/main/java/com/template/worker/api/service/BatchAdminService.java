package com.template.worker.api.service;

import org.springframework.stereotype.Service;

import com.template.worker.api.dto.RunBatchRequest;
import com.template.worker.common.launcher.BatchJobLauncher;

import lombok.RequiredArgsConstructor;

/** Batch 실행을 담당하는 서비스 레이어 - 실제 배치 실행은 BatchJobLauncher에 위임한다. */
@Service
@RequiredArgsConstructor
public class BatchAdminService {
    private final BatchJobLauncher launcher;

    public void run(RunBatchRequest request) throws Exception {
        launcher.run(request.getJobName(), request.getParams());
    }
}
