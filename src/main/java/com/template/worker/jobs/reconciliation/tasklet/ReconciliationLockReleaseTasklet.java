package com.template.worker.jobs.reconciliation.tasklet;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final BatchLockManager batchLockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        return batchLockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "reconciliation";
    }

    @Override
    protected String lockKeyContextName() {
        return BatchJobConstants.JOB_CONTEXT_LOCK_KEY;
    }

    @Override
    protected String lockOwnerContextName() {
        return BatchJobConstants.JOB_CONTEXT_LOCK_OWNER;
    }

    @Override
    protected void onLockReleased(ExecutionContext jobContext, boolean released) {
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_RELEASED,
                String.valueOf(released));
    }
}
