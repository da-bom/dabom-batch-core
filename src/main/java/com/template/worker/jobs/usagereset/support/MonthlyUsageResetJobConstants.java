package com.template.worker.jobs.usagereset.support;

import java.time.ZoneId;

public final class MonthlyUsageResetJobConstants {

    public static final ZoneId KST_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static final String JOB_NAME = "monthly-usage-reset-job";
    public static final String PARAM_TARGET_MONTH = "targetMonth";

    public static final String STEP_ACQUIRE_MONTHLY_RESET_LOCK = "acquire-monthly-reset-lock-step";
    public static final String STEP_RESET_FAMILY_MONTH = "reset-family-month-step";
    public static final String STEP_RESET_REDIS_FAMILY_KEYS = "reset-redis-family-keys-step";
    public static final String STEP_RESET_REDIS_CUSTOMER_MONTHLY_USAGE =
            "reset-redis-customer-monthly-usage-step";
    public static final String STEP_RELEASE_MONTHLY_RESET_LOCK = "release-monthly-reset-lock-step";

    public static final String EXIT_STATUS_LOCK_NOT_ACQUIRED = "LOCK_NOT_ACQUIRED";

    public static final String JOB_CONTEXT_TARGET_MONTH = "targetMonth";
    public static final String JOB_CONTEXT_LOCK_KEY = "lockKey";
    public static final String JOB_CONTEXT_LOCK_OWNER = "lockOwner";
    public static final String JOB_CONTEXT_DB_UPDATED_FAMILY_COUNT = "dbUpdatedFamilyCount";

    private MonthlyUsageResetJobConstants() {}
}
