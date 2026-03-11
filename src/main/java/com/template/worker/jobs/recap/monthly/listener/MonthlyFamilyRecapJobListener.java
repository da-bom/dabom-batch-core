package com.template.worker.jobs.recap.monthly.listener;

import java.util.Objects;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapLockManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapJobListener implements JobExecutionListener {

    private final MonthlyFamilyRecapLockManager lockManager;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 사전 처리 없음
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // 실행 컨텍스트와 파라미터에서 요약 로그 기준값 수집
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        String targetMonthFromParam =
                Objects.requireNonNullElse(
                        jobExecution
                                .getJobParameters()
                                .getString(MonthlyFamilyRecapJobConstants.PARAM_TARGET_MONTH),
                        MonthlyFamilyRecapJobConstants.JOB_CONTEXT_TARGET_MONTH_DEFAULT);

        String targetMonth =
                jobContext.getString(
                        MonthlyFamilyRecapJobConstants.JOB_CONTEXT_TARGET_MONTH,
                        targetMonthFromParam);

        long upsertedCount =
                findStepWriteCount(
                        jobExecution, MonthlyFamilyRecapJobConstants.STEP_PROCESS_MONTHLY_RECAP);

        int failureCount = jobExecution.getAllFailureExceptions().size();
        log.info(
                "Monthly family recap summary. targetMonth={}, upsertedCount={}, failureCount={},"
                        + " status={}",
                targetMonth,
                upsertedCount,
                failureCount,
                jobExecution.getStatus());

        // 예외 경로에서도 락 누수를 막기 위해 마지막에 한 번 더 정리
        String lockKey =
                jobContext.getString(MonthlyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_KEY, null);
        String lockOwner =
                jobContext.getString(MonthlyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_OWNER, null);
        boolean released = lockManager.releaseIfOwner(lockKey, lockOwner);
        if (lockKey != null) {
            log.info(
                    "Monthly family recap final lock cleanup. lockKey={}, released={}",
                    lockKey,
                    released);
        }
    }

    private long findStepWriteCount(JobExecution jobExecution, String stepName) {
        // 집계 스텝 writeCount를 조회해 처리 건수로 사용
        return jobExecution.getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(stepName))
                .findFirst()
                .map(StepExecution::getWriteCount)
                .orElse(0L);
    }
}
