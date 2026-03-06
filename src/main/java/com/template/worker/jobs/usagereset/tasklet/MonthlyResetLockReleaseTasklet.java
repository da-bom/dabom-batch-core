package com.template.worker.jobs.usagereset.tasklet;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;
import com.template.worker.jobs.usagereset.support.MonthlyResetLockManager;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyResetLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final MonthlyResetLockManager monthlyResetLockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        return monthlyResetLockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "monthly usage reset";
    }

    @Override
    protected String lockKeyContextName() {
        return MonthlyUsageResetJobConstants.JOB_CONTEXT_LOCK_KEY;
    }

    @Override
    protected String lockOwnerContextName() {
        return MonthlyUsageResetJobConstants.JOB_CONTEXT_LOCK_OWNER;
    }
}
