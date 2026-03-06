package com.template.worker.jobs.reconciliation.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DbRedisReconciliationLockKeyGenerator {

    private static final String BATCH_LOCK_PREFIX = "batch:lock:reconciliation";
    private final TargetMonthLockKeyGenerator targetMonthLockKeyGenerator;

    public String reconciliationLockKey(LocalDate targetMonth) {
        return targetMonthLockKeyGenerator.targetMonthLockKey(BATCH_LOCK_PREFIX, targetMonth);
    }
}
