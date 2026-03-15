package com.template.worker.jobs.usageprecreate.support;

import java.time.ZoneId;

public final class MonthlyUsagePrecreateJobConstants {

    public static final String KST_ZONE_ID_NAME = "Asia/Seoul";
    public static final ZoneId KST_ZONE_ID = ZoneId.of(KST_ZONE_ID_NAME);

    public static final String JOB_NAME = "monthly-usage-precreate-job";
    public static final String PARAM_TARGET_MONTH = "targetMonth";

    public static final String STEP_ACQUIRE_MONTHLY_USAGE_PRECREATE_LOCK =
            "acquire-monthly-usage-precreate-lock-step";
    public static final String STEP_PRECREATE_CUSTOMER_QUOTA = "precreate-customer-quota-step";
    public static final String STEP_PRECREATE_FAMILY_QUOTA = "precreate-family-quota-step";
    public static final String STEP_RELEASE_MONTHLY_USAGE_PRECREATE_LOCK =
            "release-monthly-usage-precreate-lock-step";

    public static final String EXIT_STATUS_LOCK_NOT_ACQUIRED = "LOCK_NOT_ACQUIRED";

    public static final String JOB_CONTEXT_TARGET_MONTH = "targetMonth";
    public static final String JOB_CONTEXT_TARGET_MONTH_DEFAULT = "default";
    public static final String JOB_CONTEXT_LOCK_KEY = "lockKey";
    public static final String JOB_CONTEXT_LOCK_OWNER = "lockOwner";
    public static final String JOB_CONTEXT_PRECREATED_CUSTOMER_QUOTA_COUNT =
            "precreatedCustomerQuotaCount";
    public static final String JOB_CONTEXT_PRECREATED_FAMILY_QUOTA_COUNT =
            "precreatedFamilyQuotaCount";
    public static final String JOB_CONTEXT_SKIPPED_FAMILY_QUOTA_COUNT = "skippedFamilyQuotaCount";

    private MonthlyUsagePrecreateJobConstants() {}
}
