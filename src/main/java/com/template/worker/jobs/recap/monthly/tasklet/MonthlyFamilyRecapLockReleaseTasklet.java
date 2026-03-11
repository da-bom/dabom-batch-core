package com.template.worker.jobs.recap.monthly.tasklet;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapLockManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final MonthlyFamilyRecapLockManager lockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        // 소유자 일치 여부를 확인해 락 해제
        return lockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "monthly family recap";
    }

    @Override
    protected String lockKeyContextName() {
        return MonthlyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_KEY;
    }

    @Override
    protected String lockOwnerContextName() {
        return MonthlyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_OWNER;
    }
}
