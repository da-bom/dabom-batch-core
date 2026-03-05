package com.template.worker.jobs.usagereset.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

@Component
public class MonthlyUsageResetLockKeyGenerator {

    private static final String BATCH_LOCK_PREFIX = "batch:lock:monthly-usage-reset";

    public String monthlyResetLockKey(LocalDate targetMonth) {
        return BATCH_LOCK_PREFIX + ":" + targetMonth;
    }
}
