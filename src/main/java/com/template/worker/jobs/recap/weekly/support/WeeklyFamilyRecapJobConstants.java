package com.template.worker.jobs.recap.weekly.support;

public final class WeeklyFamilyRecapJobConstants {

    public static final String BATCH_LOCK_PREFIX = "batch:lock:weekly-family-recap";

    // 잡/파라미터 식별자
    public static final String JOB_NAME = "weekly-family-recap-job";
    public static final String PARAM_WEEK_START_DATE = "weekStartDate";

    // 스텝 이름
    public static final String STEP_ACQUIRE_WEEKLY_RECAP_LOCK = "acquire-weekly-recap-lock-step";
    public static final String STEP_PROCESS_WEEKLY_RECAP = "process-weekly-recap-step";
    public static final String STEP_RELEASE_WEEKLY_RECAP_LOCK = "release-weekly-recap-lock-step";

    // 실행 컨텍스트 키
    public static final String JOB_CONTEXT_WEEK_START_DATE = "weekStartDate";
    public static final String JOB_CONTEXT_WEEK_START_DATE_DEFAULT = "default";

    private WeeklyFamilyRecapJobConstants() {}
}
