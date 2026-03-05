package com.template.worker.jobs.usagereset.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyUsageResetLockKeyGenerator {

    private static final String BATCH_LOCK_PREFIX = "batch:lock:monthly-usage-reset";
    private final TargetMonthLockKeyGenerator targetMonthLockKeyGenerator;

    public String monthlyResetLockKey(LocalDate targetMonth) {
        return targetMonthLockKeyGenerator.targetMonthLockKey(BATCH_LOCK_PREFIX, targetMonth);
    }
}
