package com.template.worker.jobs.usagereset.listener;

import java.util.Objects;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.usagereset.support.MonthlyResetLockManager;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyUsageResetJobListener implements JobExecutionListener {

    private final MonthlyResetLockManager monthlyResetLockManager;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 이 리스너는 잡 시작 전 선행 작업이 없고 종료 후 요약/락 정리만 담당함
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // JobExecutionContext와 StepExecution에서 요약 지표를 수집함
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        String targetMonthFromParam =
                Objects.requireNonNullElse(
                        jobExecution
                                .getJobParameters()
                                .getString(MonthlyUsageResetJobConstants.PARAM_TARGET_MONTH),
                        MonthlyUsageResetJobConstants.JOB_CONTEXT_TARGET_MONTH_DEFAULT);
        String targetMonth =
                jobContext.getString(
                        MonthlyUsageResetJobConstants.JOB_CONTEXT_TARGET_MONTH,
                        targetMonthFromParam);

        long dbUpdatedCount =
                jobContext.getLong(
                        MonthlyUsageResetJobConstants.JOB_CONTEXT_DB_UPDATED_FAMILY_COUNT, 0L);

        long redisFamilyResetCount =
                findStepWriteCount(
                        jobExecution, MonthlyUsageResetJobConstants.STEP_RESET_REDIS_FAMILY_KEYS);
        long redisCustomerResetCount =
                findStepWriteCount(
                        jobExecution,
                        MonthlyUsageResetJobConstants.STEP_RESET_REDIS_CUSTOMER_MONTHLY_USAGE);

        int failureCount = jobExecution.getAllFailureExceptions().size();
        log.info(
                "Monthly usage reset summary. targetMonth={}, dbUpdatedFamilyCount={}, "
                        + "redisFamilyKeyResetCount={}, redisCustomerMonthlyUsageResetCount={}, "
                        + "failureCount={}, status={}",
                targetMonth,
                dbUpdatedCount,
                redisFamilyResetCount,
                redisCustomerResetCount,
                failureCount,
                jobExecution.getStatus());

        // 예외 경로 누수 방지를 위해 최종 락 정리를 한 번 더 수행함
        String lockKey =
                jobContext.getString(MonthlyUsageResetJobConstants.JOB_CONTEXT_LOCK_KEY, null);
        String lockOwner =
                jobContext.getString(MonthlyUsageResetJobConstants.JOB_CONTEXT_LOCK_OWNER, null);
        boolean released = monthlyResetLockManager.releaseIfOwner(lockKey, lockOwner);
        if (lockKey != null) {
            log.info(
                    "Monthly usage reset final lock cleanup. lockKey={}, released={}",
                    lockKey,
                    released);
        }
    }

    private long findStepWriteCount(JobExecution jobExecution, String stepName) {
        // 대상 step의 writeCount를 조회해 처리 건수 집계에 사용함
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStepName().equals(stepName)) {
                return stepExecution.getWriteCount();
            }
        }
        return 0L;
    }
}
