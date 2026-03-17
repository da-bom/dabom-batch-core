package com.template.worker.jobs.reconciliation.support;

public final class DbRedisReconciliationJobConstants {

    public static final String BATCH_LOCK_PREFIX = "batch:lock:reconciliation";

    public static final String JOB_NAME = "db-redis-reconciliation-job";
    public static final String PARAM_TARGET_MONTH = "targetMonth";

    public static final String STEP_ACQUIRE_RECONCILIATION_LOCK =
            "acquire-reconciliation-lock-step";
    public static final String STEP_INVALIDATE_FAMILY_INFO_AND_REMAINING =
            "invalidate-family-info-and-remaining-step";
    public static final String STEP_INVALIDATE_CUSTOMER_MONTHLY_USAGE =
            "invalidate-customer-monthly-usage-step";
    public static final String STEP_RELEASE_RECONCILIATION_LOCK =
            "release-reconciliation-lock-step";

    public static final String JOB_CONTEXT_TARGET_MONTH = "targetMonth";
    public static final String JOB_CONTEXT_TARGET_MONTH_DEFAULT = "default";
    public static final String JOB_CONTEXT_LOCK_STATUS = "lockStatus";
    public static final String JOB_CONTEXT_LOCK_RELEASED = "lockReleased";

    public static final String LOCK_STATUS_ACQUIRED = "ACQUIRED";
    public static final String LOCK_STATUS_NOT_ACQUIRED = "NOT_ACQUIRED";

    // StepExecutionContext 요약 집계 키
    public static final String STEP_CONTEXT_DELETED_FAMILY_INFO_COUNT = "deletedFamilyInfoCount";
    public static final String STEP_CONTEXT_DELETED_FAMILY_REMAINING_COUNT =
            "deletedFamilyRemainingCount";
    public static final String STEP_CONTEXT_DELETED_CUSTOMER_MONTHLY_USAGE_COUNT =
            "deletedCustomerMonthlyUsageCount";

    private DbRedisReconciliationJobConstants() {}
}
