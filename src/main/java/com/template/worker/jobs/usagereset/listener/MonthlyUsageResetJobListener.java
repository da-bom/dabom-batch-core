package com.template.worker.jobs.usagereset.listener;

import java.util.Objects;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyUsageResetJobListener implements JobExecutionListener {

    private final BatchLockManager batchLockManager;

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

        long deletedPreviousMonthFamilyKeyCount =
                findStepExecutionContextLong(
                        jobExecution,
                        MonthlyUsageResetJobConstants.STEP_RESET_REDIS_FAMILY_KEYS,
                        MonthlyUsageResetJobConstants.STEP_CONTEXT_DELETED_FAMILY_KEY_COUNT);
        long deletedPreviousMonthCustomerUsageKeyCount =
                findStepExecutionContextLong(
                        jobExecution,
                        MonthlyUsageResetJobConstants.STEP_RESET_REDIS_CUSTOMER_MONTHLY_USAGE,
                        MonthlyUsageResetJobConstants
                                .STEP_CONTEXT_DELETED_CUSTOMER_MONTHLY_USAGE_KEY_COUNT);

        int failureCount = jobExecution.getAllFailureExceptions().size();
        log.info(
                "Monthly redis cleanup summary. targetMonth={},"
                    + " deletedPreviousMonthFamilyKeyCount={},"
                    + " deletedPreviousMonthCustomerUsageKeyCount={}, failureCount={}, status={}",
                targetMonth,
                deletedPreviousMonthFamilyKeyCount,
                deletedPreviousMonthCustomerUsageKeyCount,
                failureCount,
                jobExecution.getStatus());

        String lockKey = jobContext.getString(BatchJobConstants.JOB_CONTEXT_LOCK_KEY, null);
        String lockOwner = jobContext.getString(BatchJobConstants.JOB_CONTEXT_LOCK_OWNER, null);
        try {
            boolean released = batchLockManager.releaseIfOwner(lockKey, lockOwner);
            if (lockKey != null) {
                log.info(
                        "Monthly usage reset final lock cleanup. lockKey={}, released={}",
                        lockKey,
                        released);
            }
        } catch (Exception exception) {
            log.error(
                    "Monthly usage reset final lock cleanup failed. lockKey={}",
                    lockKey,
                    exception);
        }
    }

    private long findStepExecutionContextLong(
            JobExecution jobExecution, String stepName, String contextKey) {
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStepName().equals(stepName)) {
                return stepExecution.getExecutionContext().getLong(contextKey, 0L);
            }
        }
        return 0L;
    }
}
