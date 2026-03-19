package com.template.worker.common.listener;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 배치 실행 결과를 수집하는 공통 로거 */
@Slf4j
@Component
public class JobLogger {
    public void jobSuccess(Long jobExecutionId, String jobName, long durationMs) {
        log.info(
                "[BATCH SUCCESS] job={} jobExecutionId={} duration={}ms",
                jobName,
                jobExecutionId,
                durationMs);
    }

    public void jobFailed(Long jobExecutionId, String jobName, long durationMs, Throwable cause) {
        log.error(
                "[BATCH FAILED] job={} jobExecutionId={} duration={}ms",
                jobName,
                jobExecutionId,
                durationMs,
                cause);
    }
}
