package com.template.worker.jobs.recap.weekly.tasklet;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.tasklet.AbstractLockReleaseTasklet;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapLockManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapLockReleaseTasklet extends AbstractLockReleaseTasklet {

    private final WeeklyFamilyRecapLockManager lockManager;

    @Override
    protected boolean releaseIfOwner(String lockKey, String lockOwner) {
        // 소유자 일치 여부를 확인해 락 해제
        return lockManager.releaseIfOwner(lockKey, lockOwner);
    }

    @Override
    protected String lockLogName() {
        return "weekly family recap";
    }

    @Override
    protected String lockKeyContextName() {
        return WeeklyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_KEY;
    }

    @Override
    protected String lockOwnerContextName() {
        return WeeklyFamilyRecapJobConstants.JOB_CONTEXT_LOCK_OWNER;
    }
}
