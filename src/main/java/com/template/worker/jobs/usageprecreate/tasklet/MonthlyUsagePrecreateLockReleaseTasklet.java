package com.template.worker.jobs.usageprecreate.tasklet;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateLockManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyUsagePrecreateLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final MonthlyUsagePrecreateLockManager lockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        return lockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "monthly usage precreate";
    }

    @Override
    protected String lockKeyContextName() {
        return MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_LOCK_KEY;
    }

    @Override
    protected String lockOwnerContextName() {
        return MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_LOCK_OWNER;
    }
}
