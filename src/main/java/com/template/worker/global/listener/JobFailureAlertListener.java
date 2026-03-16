package com.template.worker.global.listener;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.retry.BatchRetrySupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobFailureAlertListener implements JobExecutionListener {

    // Lettuce/Spring Redis는 Redis timeout을 QueryTimeoutException으로 번역하기도 한다.
    private static final String REDIS_TIMEOUT_MESSAGE = "Redis command timed out";
    private static final String REDIS_COMMAND_TIMEOUT_EXCEPTION = "RedisCommandTimeoutException";
    private static final String PARAM_TARGET_MONTH = "targetMonth";
    private static final String PARAM_WEEK_START_DATE = "weekStartDate";
    private static final String UNKNOWN_ERROR_SUMMARY = "알 수 없는 오류로 최종 실패";

    private final BatchAlertService batchAlertService;
    private final BatchRetrySupport batchRetrySupport;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 사전 처리 없음
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // 재시도 중간 실패는 무시하고, 최종 FAILED인 경우에만 운영 알람을 보냄
        if (jobExecution.getStatus() != BatchStatus.FAILED) {
            return;
        }

        String jobName = jobExecution.getJobInstance().getJobName();
        String parameterSummary = resolveParameterSummary(jobExecution.getJobParameters());
        FailureDetail failureDetail = resolveFailureDetail(jobExecution);

        batchAlertService.sendJobFailureAlert(
                jobName,
                jobExecution.getId(),
                parameterSummary,
                failureDetail.summary(),
                failureDetail.technicalDetail());
    }

    private String resolveParameterSummary(JobParameters jobParameters) {
        // 알람 본문에는 운영자가 자주 보는 핵심 파라미터만 노출
        List<String> segments = new ArrayList<>();
        appendIfPresent(segments, PARAM_TARGET_MONTH, jobParameters.getString(PARAM_TARGET_MONTH));
        appendIfPresent(
                segments, PARAM_WEEK_START_DATE, jobParameters.getString(PARAM_WEEK_START_DATE));

        if (segments.isEmpty()) {
            return "parameters=none";
        }

        return String.join(", ", segments);
    }

    private void appendIfPresent(List<String> segments, String key, String value) {
        if (StringUtils.hasText(value)) {
            segments.add(key + "=" + value);
        }
    }

    private FailureDetail resolveFailureDetail(JobExecution jobExecution) {
        // 최상위 예외와 root cause를 함께 봐야 retry wrapper와 실제 원인을 모두 판단할 수 있음
        if (jobExecution.getAllFailureExceptions().isEmpty()) {
            return new FailureDetail(UNKNOWN_ERROR_SUMMARY, "Unknown error");
        }

        Throwable exception = jobExecution.getAllFailureExceptions().get(0);
        Throwable rootCause = findRootCause(exception);
        String technicalDetail = buildTechnicalDetail(rootCause);
        String summary = buildSummary(exception, rootCause);
        return new FailureDetail(summary, technicalDetail);
    }

    private Throwable findRootCause(Throwable exception) {
        // 알람 요약은 가장 안쪽 실제 예외를 기준으로 만듦
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String buildSummary(Throwable exception, Throwable rootCause) {
        // 같은 QueryTimeoutException이라도 DB/Redis 의미가 다를 수 있어 순서 분리
        boolean retryExhausted = containsRetryExhaustedSignal(exception);

        if (rootCause instanceof RedisConnectionFailureException) {
            return retryExhausted
                    ? String.format(
                            "Redis 연결 실패로 재시도 %d회 후 최종 실패", batchRetrySupport.getRetryLimit())
                    : "Redis 연결 실패";
        }
        if (isRedisTimeout(rootCause)) {
            return retryExhausted
                    ? String.format(
                            "Redis 명령 타임아웃으로 재시도 %d회 후 최종 실패", batchRetrySupport.getRetryLimit())
                    : "Redis 명령 타임아웃";
        }
        if (rootCause instanceof PessimisticLockingFailureException) {
            return retryExhausted
                    ? String.format("DB 데드락으로 재시도 %d회 후 최종 실패", batchRetrySupport.getRetryLimit())
                    : "DB 데드락 발생";
        }
        if (rootCause instanceof QueryTimeoutException) {
            return retryExhausted
                    ? String.format(
                            "DB 쿼리 타임아웃으로 재시도 %d회 후 최종 실패", batchRetrySupport.getRetryLimit())
                    : "DB 쿼리 타임아웃";
        }
        if (retryExhausted) {
            return String.format("재시도 %d회 후 최종 실패", batchRetrySupport.getRetryLimit());
        }
        if (StringUtils.hasText(rootCause.getMessage())) {
            return rootCause.getMessage();
        }
        return UNKNOWN_ERROR_SUMMARY;
    }

    private boolean isRedisTimeout(Throwable exception) {
        // Root cause가 QueryTimeoutException이 아니어도 RedisCommandTimeoutException이면 Redis timeout으로 봄
        Throwable current = exception;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())
                    && current.getMessage().contains(REDIS_TIMEOUT_MESSAGE)) {
                return true;
            }
            if (REDIS_COMMAND_TIMEOUT_EXCEPTION.equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsRetryExhaustedSignal(Throwable exception) {
        // Spring Batch retry wrapper 여부를 확인해 "재시도 n회 후 최종 실패" 문구를 붙임
        Throwable current = exception;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())
                    && current.getMessage().contains("Retry exhausted")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String buildTechnicalDetail(Throwable exception) {
        // 운영 알람에는 검색 가능한 원본 타입/메시지를 함께 남김
        if (StringUtils.hasText(exception.getMessage())) {
            return exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private record FailureDetail(String summary, String technicalDetail) {}
}
