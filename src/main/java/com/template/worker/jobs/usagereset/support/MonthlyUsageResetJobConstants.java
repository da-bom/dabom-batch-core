package com.template.worker.jobs.usagereset.support;

import java.util.List;

public final class MonthlyUsageResetJobConstants {

    public static final String BATCH_LOCK_PREFIX = "batch:lock:monthly-usage-reset";

    public static final String JOB_NAME = "monthly-usage-reset-job";
    public static final String PARAM_TARGET_MONTH = "targetMonth";

    public static final String STEP_ACQUIRE_MONTHLY_RESET_LOCK = "acquire-monthly-reset-lock-step";
    public static final String STEP_RESET_REDIS_FAMILY_KEYS = "reset-redis-family-keys-step";
    public static final String STEP_RESET_REDIS_CUSTOMER_MONTHLY_USAGE =
            "reset-redis-customer-monthly-usage-step";
    public static final String STEP_RELEASE_MONTHLY_RESET_LOCK = "release-monthly-reset-lock-step";

    public static final String JOB_CONTEXT_TARGET_MONTH = "targetMonth";
    public static final String JOB_CONTEXT_TARGET_MONTH_DEFAULT = "default";

    public static final String STEP_CONTEXT_DELETED_FAMILY_KEY_COUNT =
            "deletedPreviousMonthFamilyKeyCount";
    public static final String STEP_CONTEXT_DELETED_CUSTOMER_MONTHLY_USAGE_KEY_COUNT =
            "deletedPreviousMonthCustomerUsageKeyCount";

    public static final List<Integer> ALERT_THRESHOLDS = List.of(10, 30, 50);

    private MonthlyUsageResetJobConstants() {}
}
