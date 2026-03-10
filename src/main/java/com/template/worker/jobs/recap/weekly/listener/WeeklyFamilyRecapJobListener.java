package com.template.worker.jobs.recap.weekly.listener;

import java.util.Objects;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapLockManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapJobListener implements JobExecutionListener {

    private final WeeklyFamilyRecapLockManager lockManager;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 사전 처리 없음
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // 실행 컨텍스트와 파라미터에서 요약 로그 기준값을 수집
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        String weekStartDateFromParam =
                Objects.requireNonNullElse(
                        jobExecution
                                .getJobParameters()
                                .getString(WeeklyFamilyRecapJobConstants.PARAM_WEEK_START_DATE),
                        WeeklyFamilyRecapJobConstants.JOB_CONTEXT_WEEK_START_DATE_DEFAULT);

        String weekStartDate =
                jobContext.getString(
                        WeeklyFamilyRecapJobConstants.JOB_CONTEXT_WEEK_START_DATE,
                        weekStartDateFromParam);

        long upsertedCount =
                findStepWriteCount(
                        jobExecution, WeeklyFamilyRecapJobConstants.STEP_AGGREGATE_WEEKLY_RECAP);

        int failureCount = jobExecution.getAllFailureExceptions().size();
        log.info(
                "Weekly family recap summary. weekStartDate={}, upsertedCount={}, "
                        + "failureCount={}, status={}",
                weekStartDate,
                upsertedCount,
                failureCount,
                jobExecution.getStatus());

        // 예외 경로에서도 락 누수를 막기 위해 마지막에 한 번 더 정리
        String lockKey =
                jobContext.getString(WeeklyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_KEY, null);
        String lockOwner =
                jobContext.getString(WeeklyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_OWNER, null);
        boolean released = lockManager.releaseIfOwner(lockKey, lockOwner);
        if (lockKey != null) {
            log.info(
                    "Weekly family recap final lock cleanup. lockKey={}, released={}",
                    lockKey,
                    released);
        }
    }

    private long findStepWriteCount(JobExecution jobExecution, String stepName) {
        // 집계 스텝 writeCount를 조회해 처리 건수로 사용
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStepName().equals(stepName)) {
                return stepExecution.getWriteCount();
            }
        }
        return 0L;
    }
}
