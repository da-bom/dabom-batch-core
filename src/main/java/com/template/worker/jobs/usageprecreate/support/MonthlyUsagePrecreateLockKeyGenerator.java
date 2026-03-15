package com.template.worker.jobs.usageprecreate.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyUsagePrecreateLockKeyGenerator {

    private static final String BATCH_LOCK_PREFIX = "batch:lock:monthly-usage-precreate";
    private final TargetMonthLockKeyGenerator targetMonthLockKeyGenerator;

    public String monthlyUsagePrecreateLockKey(LocalDate targetMonth) {
        return targetMonthLockKeyGenerator.targetMonthLockKey(BATCH_LOCK_PREFIX, targetMonth);
    }
}
