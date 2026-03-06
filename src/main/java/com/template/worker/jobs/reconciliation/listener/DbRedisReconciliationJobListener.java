package com.template.worker.jobs.reconciliation.listener;

import java.util.Objects;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationLockManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbRedisReconciliationJobListener implements JobExecutionListener {

    private final DbRedisReconciliationLockManager lockManager;

    @Override
    public void beforeJob(JobExecution jobExecution) {}

    @Override
    public void afterJob(JobExecution jobExecution) {
        // JobExecutionContext와 StepExecutionContext에서 요약 지표를 수집함
        ExecutionContext jobContext = jobExecution.getExecutionContext();
        String targetMonthFromParam =
                Objects.requireNonNullElse(
                        jobExecution
                                .getJobParameters()
                                .getString(DbRedisReconciliationJobConstants.PARAM_TARGET_MONTH),
                        "default");
        String targetMonth =
                jobContext.getString(
                        DbRedisReconciliationJobConstants.JOB_CONTEXT_TARGET_MONTH,
                        targetMonthFromParam);
        String lockStatus =
                jobContext.getString(
                        DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_STATUS,
                        DbRedisReconciliationJobConstants.LOCK_STATUS_NOT_ACQUIRED);

        long deletedFamilyInfoCount =
                findStepContextLong(
                        jobExecution,
                        DbRedisReconciliationJobConstants.STEP_INVALIDATE_FAMILY_INFO_AND_REMAINING,
                        DbRedisReconciliationJobConstants.STEP_CONTEXT_DELETED_FAMILY_INFO_COUNT);
        long deletedFamilyRemainingCount =
                findStepContextLong(
                        jobExecution,
                        DbRedisReconciliationJobConstants.STEP_INVALIDATE_FAMILY_INFO_AND_REMAINING,
                        DbRedisReconciliationJobConstants
                                .STEP_CONTEXT_DELETED_FAMILY_REMAINING_COUNT);
        long deletedCustomerMonthlyUsageCount =
                findStepContextLong(
                        jobExecution,
                        DbRedisReconciliationJobConstants.STEP_INVALIDATE_CUSTOMER_MONTHLY_USAGE,
                        DbRedisReconciliationJobConstants
                                .STEP_CONTEXT_DELETED_CUSTOMER_MONTHLY_USAGE_COUNT);

        int failureCount = jobExecution.getAllFailureExceptions().size();
        log.info(
                "DB-Redis reconciliation summary. targetMonth={}, deletedFamilyInfoCount={}, "
                        + "deletedFamilyRemainingCount={}, deletedCustomerMonthlyUsageCount={}, "
                        + "failureCount={}, lockStatus={}, status={}, jobExecutionId={}",
                targetMonth,
                deletedFamilyInfoCount,
                deletedFamilyRemainingCount,
                deletedCustomerMonthlyUsageCount,
                failureCount,
                lockStatus,
                jobExecution.getStatus(),
                jobExecution.getId());

        // 예외 경로 누수 방지를 위해 최종 락 정리를 한 번 더 수행함
        String lockKey =
                jobContext.getString(DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_KEY, null);
        String lockOwner =
                jobContext.getString(
                        DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_OWNER, null);
        boolean released = lockManager.releaseIfOwner(lockKey, lockOwner);
        if (released) {
            jobContext.putString(
                    DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_RELEASED,
                    String.valueOf(true));
        }

        if (lockKey != null) {
            log.info(
                    "DB-Redis reconciliation final lock cleanup. lockKey={}, released={}",
                    lockKey,
                    released);
        }
    }

    private long findStepContextLong(
            JobExecution jobExecution, String stepName, String executionContextKey) {
        // 대상 step의 execution context 값을 조회해 처리 건수 집계에 사용함
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStepName().equals(stepName)) {
                return stepExecution.getExecutionContext().getLong(executionContextKey, 0L);
            }
        }
        return 0L;
    }
}
