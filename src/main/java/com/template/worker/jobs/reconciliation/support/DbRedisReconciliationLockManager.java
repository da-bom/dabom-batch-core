package com.template.worker.jobs.reconciliation.support;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchLockManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DbRedisReconciliationLockManager {

    private final BatchLockManager batchLockManager;

    public boolean tryAcquire(String lockKey, String lockOwner, Duration ttl) {
        return batchLockManager.tryAcquire(lockKey, lockOwner, ttl);
    }

    public boolean releaseIfOwner(String lockKey, String lockOwner) {
        return batchLockManager.releaseIfOwner(lockKey, lockOwner);
    }
}
