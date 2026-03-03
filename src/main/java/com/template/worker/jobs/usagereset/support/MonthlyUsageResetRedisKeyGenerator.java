package com.template.worker.jobs.usagereset.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

@Component
public class MonthlyUsageResetRedisKeyGenerator {
    private static final String KEY_SEPARATOR = ":";
    private static final String FAMILY_PREFIX = "family";
    private static final String CUSTOMER_KEY = "customer";
    private static final String ALERT_KEY = "alert";
    private static final String THRESHOLD_KEY = "THRESHOLD";
    private static final String REMAINING_KEY = "remaining";
    private static final String USAGE_KEY = "usage";
    private static final String MONTHLY_KEY = "monthly";
    private static final String BATCH_LOCK_PREFIX = "batch:lock:monthly-usage-reset";

    public String monthlyResetLockKey(LocalDate targetMonth) {
        return BATCH_LOCK_PREFIX + KEY_SEPARATOR + targetMonth;
    }

    public String familyRemainingKey(Long familyId) {
        return FAMILY_PREFIX + KEY_SEPARATOR + familyId + KEY_SEPARATOR + REMAINING_KEY;
    }

    public String familyAlertThresholdKey(Long familyId, int threshold) {
        return FAMILY_PREFIX
                + KEY_SEPARATOR
                + familyId
                + KEY_SEPARATOR
                + ALERT_KEY
                + KEY_SEPARATOR
                + THRESHOLD_KEY
                + KEY_SEPARATOR
                + threshold;
    }

    public String customerMonthlyUsageKey(Long familyId, Long customerId) {
        return FAMILY_PREFIX
                + KEY_SEPARATOR
                + familyId
                + KEY_SEPARATOR
                + CUSTOMER_KEY
                + KEY_SEPARATOR
                + customerId
                + KEY_SEPARATOR
                + USAGE_KEY
                + KEY_SEPARATOR
                + MONTHLY_KEY;
    }
}
