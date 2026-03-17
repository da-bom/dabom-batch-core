package com.template.worker.global.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

@Component
public class RedisKeyGenerator {
    private static final String KEY_SEPARATOR = ":";
    private static final String FAMILY_PREFIX = "family";
    private static final String CUSTOMER_KEY = "customer";
    private static final String ALERT_KEY = "alert";
    private static final String THRESHOLD_KEY = "THRESHOLD";
    private static final String REMAINING_KEY = "remaining";
    private static final String INFO_KEY = "info";
    private static final String USAGE_KEY = "usage";
    private static final String MONTHLY_KEY = "monthly";
    private static final DateTimeFormatter MONTH_SUFFIX_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMM");

    public String familyRemainingKey(Long familyId, LocalDate targetMonth) {
        return FAMILY_PREFIX
                + KEY_SEPARATOR
                + familyId
                + KEY_SEPARATOR
                + REMAINING_KEY
                + KEY_SEPARATOR
                + targetMonth.format(MONTH_SUFFIX_FORMATTER);
    }

    public String familyInfoKey(Long familyId, LocalDate targetMonth) {
        return FAMILY_PREFIX
                + KEY_SEPARATOR
                + familyId
                + KEY_SEPARATOR
                + INFO_KEY
                + KEY_SEPARATOR
                + targetMonth.format(MONTH_SUFFIX_FORMATTER);
    }

    public String familyAlertThresholdKey(Long familyId, int threshold, LocalDate targetMonth) {
        return FAMILY_PREFIX
                + KEY_SEPARATOR
                + familyId
                + KEY_SEPARATOR
                + ALERT_KEY
                + KEY_SEPARATOR
                + THRESHOLD_KEY
                + KEY_SEPARATOR
                + threshold
                + KEY_SEPARATOR
                + targetMonth.format(MONTH_SUFFIX_FORMATTER);
    }

    private String customerMonthlyUsageKey(Long familyId, Long customerId) {
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

    public String customerMonthlyUsageKey(Long familyId, Long customerId, LocalDate eventMonth) {
        return customerMonthlyUsageKey(familyId, customerId)
                + KEY_SEPARATOR
                + eventMonth.format(MONTH_SUFFIX_FORMATTER);
    }
}
