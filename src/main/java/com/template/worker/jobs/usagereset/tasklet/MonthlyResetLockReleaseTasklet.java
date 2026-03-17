package com.template.worker.jobs.usagereset.tasklet;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyResetLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final BatchLockManager batchLockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        return batchLockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "monthly usage reset";
    }

    @Override
    protected String lockKeyContextName() {
        return BatchJobConstants.JOB_CONTEXT_LOCK_KEY;
    }

    @Override
    protected String lockOwnerContextName() {
        return BatchJobConstants.JOB_CONTEXT_LOCK_OWNER;
    }
}
