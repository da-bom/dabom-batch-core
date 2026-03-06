package com.template.worker.jobs.reconciliation.tasklet;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.tasklet.AbstractTargetMonthLockTasklet;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobParameterSupport;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationLockKeyGenerator;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationLockManager;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationLockTasklet extends AbstractTargetMonthLockTasklet {

    private final DbRedisReconciliationLockManager lockManager;
    private final DbRedisReconciliationJobParameterSupport parameterSupport;
    private final DbRedisReconciliationProperties properties;
    private final DbRedisReconciliationLockKeyGenerator lockKeyGenerator;

    @Override
    protected LocalDate resolveTargetMonth(JobParameters jobParameters) {
        return parameterSupport.resolveTargetMonth(jobParameters);
    }

    @Override
    protected String generateLockKey(LocalDate targetMonth) {
        return lockKeyGenerator.reconciliationLockKey(targetMonth);
    }

    @Override
    protected boolean tryAcquire(String lockKey, String lockOwner, Duration ttl) {
        return lockManager.tryAcquire(lockKey, lockOwner, ttl);
    }

    @Override
    protected Duration lockTtl() {
        return properties.getLockTtl();
    }

    @Override
    protected String lockNotAcquiredExitStatus() {
        return DbRedisReconciliationJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED;
    }

    @Override
    protected String lockLogName() {
        return "reconciliation";
    }

    @Override
    protected void initializeJobContext(
            ExecutionContext jobContext, LocalDate targetMonth, String lockKey, String lockOwner) {
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_TARGET_MONTH, targetMonth.toString());
        jobContext.putString(DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_KEY, lockKey);
        jobContext.putString(DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_OWNER, lockOwner);
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_STATUS,
                DbRedisReconciliationJobConstants.LOCK_STATUS_NOT_ACQUIRED);
    }

    @Override
    protected void onLockAcquired(ExecutionContext jobContext) {
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_STATUS,
                DbRedisReconciliationJobConstants.LOCK_STATUS_ACQUIRED);
    }
}
