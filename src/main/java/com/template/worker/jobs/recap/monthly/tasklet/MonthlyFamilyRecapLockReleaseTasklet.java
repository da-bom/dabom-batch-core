package com.template.worker.jobs.recap.monthly.tasklet;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final BatchLockManager batchLockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        // 소유자 일치 여부를 확인해 락 해제
        return batchLockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "monthly family recap";
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
