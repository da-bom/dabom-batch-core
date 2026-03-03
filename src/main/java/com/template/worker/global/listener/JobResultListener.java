package com.template.worker.global.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.annotation.AfterJob;
import org.springframework.batch.core.annotation.BeforeJob;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Batch Job의 실행 결과 Listener - 모든 Job의 실행 결과를 공통 포맷으로 수집한다. - 성공/실패 여부, 실행 시간, 실패 원인을 중앙에서 통제한다. */
@Component
@RequiredArgsConstructor
public class JobResultListener {

    private final JobLogger jobLogger;

    @BeforeJob
    public void before(JobExecution jobExecution) {}

    @AfterJob
    public void after(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();

        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        long duration =
                (start != null && end != null) ? Duration.between(start, end).toMillis() : 0L;

        if (jobExecution.getStatus().isUnsuccessful()) {
            Throwable cause =
                    jobExecution.getAllFailureExceptions().isEmpty()
                            ? null
                            : jobExecution.getAllFailureExceptions().get(0);

            jobLogger.jobFailed(jobName, duration, cause);
        } else {
            jobLogger.jobSuccess(jobName, duration);
        }
    }
}
