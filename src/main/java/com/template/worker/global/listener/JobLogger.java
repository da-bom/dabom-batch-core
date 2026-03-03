package com.template.worker.global.listener;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 배치 실행 결과를 수집하는 공통 로거 */
@Slf4j
@Component
public class JobLogger {
    public void jobSuccess(String jobName, long durationMs) {
        log.info("[BATCH SUCCESS] job={} duration={}ms", jobName, durationMs);
    }

    public void jobFailed(String jobName, long durationMs, Throwable cause) {
        log.error("[BATCH FAILED] job={} duration={}ms", jobName, durationMs, cause);
    }
}
