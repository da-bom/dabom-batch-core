package com.template.worker.jobs.recap.monthly.support;

public final class MonthlyFamilyRecapJobConstants {

    public static final String BATCH_LOCK_PREFIX = "batch:lock:monthly-family-recap";

    // 잡/파라미터 식별자
    public static final String JOB_NAME = "monthly-family-recap-job";
    public static final String PARAM_TARGET_MONTH = "targetMonth";

    // 스텝 이름
    public static final String STEP_ACQUIRE_MONTHLY_RECAP_LOCK = "acquire-monthly-recap-lock-step";
    public static final String STEP_PROCESS_MONTHLY_RECAP = "process-monthly-recap-step";
    public static final String STEP_RELEASE_MONTHLY_RECAP_LOCK = "release-monthly-recap-lock-step";

    // 실행 컨텍스트 키
    public static final String JOB_CONTEXT_TARGET_MONTH = "targetMonth";
    public static final String JOB_CONTEXT_TARGET_MONTH_DEFAULT = "default";

    private MonthlyFamilyRecapJobConstants() {}
}
