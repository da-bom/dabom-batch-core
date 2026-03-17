package com.template.worker.jobs.usageprecreate.tasklet;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.tasklet.AbstractTargetMonthLockTasklet;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobParameterSupport;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateLockKeyGenerator;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateLockManager;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyUsagePrecreateLockTasklet extends AbstractTargetMonthLockTasklet {

    private final MonthlyUsagePrecreateLockManager lockManager;
    private final MonthlyUsagePrecreateJobParameterSupport parameterSupport;
    private final MonthlyUsagePrecreateProperties properties;
    private final MonthlyUsagePrecreateLockKeyGenerator lockKeyGenerator;

    @Override
    protected LocalDate resolveTargetMonth(JobParameters jobParameters) {
        return parameterSupport.resolveTargetMonth(jobParameters);
    }

    @Override
    protected String generateLockKey(LocalDate targetMonth) {
        return lockKeyGenerator.monthlyUsagePrecreateLockKey(targetMonth);
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
        return MonthlyUsagePrecreateJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED;
    }

    @Override
    protected String lockLogName() {
        return "monthly usage precreate";
    }

    @Override
    protected void initializeJobContext(
            ExecutionContext jobContext, LocalDate targetMonth, String lockKey, String lockOwner) {
        jobContext.putString(
                MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_TARGET_MONTH, targetMonth.toString());
        jobContext.putString(MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_LOCK_KEY, lockKey);
        jobContext.putString(MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_LOCK_OWNER, lockOwner);
    }
}
