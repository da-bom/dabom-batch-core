package com.template.worker.jobs.recap.weekly.support;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchLockManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapLockManager {

    private final BatchLockManager batchLockManager;

    public boolean tryAcquire(String lockKey, String lockOwner, Duration ttl) {
        // 공통 락 매니저로 주간 리캡 실행 락 획득
        return batchLockManager.tryAcquire(lockKey, lockOwner, ttl);
    }

    public boolean releaseIfOwner(String lockKey, String lockOwner) {
        // 락 소유자가 일치할 때만 안전하게 해제
        return batchLockManager.releaseIfOwner(lockKey, lockOwner);
    }
}
