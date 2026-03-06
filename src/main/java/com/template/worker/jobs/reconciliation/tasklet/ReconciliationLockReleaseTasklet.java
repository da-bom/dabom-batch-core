package com.template.worker.jobs.reconciliation.tasklet;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationLockManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final DbRedisReconciliationLockManager lockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        return lockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "reconciliation";
    }

    @Override
    protected String lockKeyContextName() {
        return DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_KEY;
    }

    @Override
    protected String lockOwnerContextName() {
        return DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_OWNER;
    }

    @Override
    protected void onLockReleased(ExecutionContext jobContext, boolean released) {
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_RELEASED,
                String.valueOf(released));
    }
}
