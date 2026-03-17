package com.template.worker.jobs.usagereset.tasklet;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;
import com.template.worker.jobs.common.tasklet.AbstractTargetMonthLockTasklet;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyResetLockTasklet extends AbstractTargetMonthLockTasklet {

    private final BatchLockManager batchLockManager;
    private final MonthlyUsageResetJobParameterSupport parameterSupport;
    private final MonthlyUsageResetProperties properties;
    private final TargetMonthLockKeyGenerator lockKeyGenerator;

    @Override
    protected LocalDate resolveTargetMonth(JobParameters jobParameters) {
        return parameterSupport.resolveTargetMonth(jobParameters);
    }

    @Override
    protected String generateLockKey(LocalDate targetMonth) {
        return lockKeyGenerator.targetMonthLockKey(
                MonthlyUsageResetJobConstants.BATCH_LOCK_PREFIX, targetMonth);
    }

    @Override
    protected boolean tryAcquire(String lockKey, String lockOwner, Duration ttl) {
        return batchLockManager.tryAcquire(lockKey, lockOwner, ttl);
    }

    @Override
    protected Duration lockTtl() {
        return properties.getLockTtl();
    }

    @Override
    protected String lockNotAcquiredExitStatus() {
        return BatchJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED;
    }

    @Override
    protected String lockLogName() {
        return "monthly usage reset";
    }

    @Override
    protected void initializeJobContext(
            ExecutionContext jobContext, LocalDate targetMonth, String lockKey, String lockOwner) {
        jobContext.putString(
                MonthlyUsageResetJobConstants.JOB_CONTEXT_TARGET_MONTH, targetMonth.toString());
        jobContext.putString(BatchJobConstants.JOB_CONTEXT_LOCK_KEY, lockKey);
        jobContext.putString(BatchJobConstants.JOB_CONTEXT_LOCK_OWNER, lockOwner);
    }
}
