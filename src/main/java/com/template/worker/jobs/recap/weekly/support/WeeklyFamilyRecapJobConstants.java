package com.template.worker.jobs.recap.weekly.support;

import java.time.ZoneId;

public final class WeeklyFamilyRecapJobConstants {

    // 주간 리캡 배치 기준 타임존
    public static final String KST_ZONE_ID_NAME = "Asia/Seoul";
    public static final ZoneId KST_ZONE_ID = ZoneId.of(KST_ZONE_ID_NAME);

    // 잡/파라미터 식별자
    public static final String JOB_NAME = "weekly-family-recap-job";
    public static final String PARAM_WEEK_START_DATE = "weekStartDate";

    // 스텝 이름
    public static final String STEP_ACQUIRE_WEEKLY_RECAP_LOCK = "acquire-weekly-recap-lock-step";
    public static final String STEP_AGGREGATE_WEEKLY_RECAP = "aggregate-weekly-recap-step";
    public static final String STEP_RELEASE_WEEKLY_RECAP_LOCK = "release-weekly-recap-lock-step";

    // 락 미획득 종료 코드
    public static final String EXIT_STATUS_LOCK_NOT_ACQUIRED = "LOCK_NOT_ACQUIRED";

    // 실행 컨텍스트 키
    public static final String JOB_CONTEXT_WEEK_START_DATE = "weekStartDate";
    public static final String JOB_CONTEXT_WEEK_START_DATE_DEFAULT = "default";
    public static final String JOB_CONTEXT_LOCK_KEY = "lockKey";
    public static final String JOB_CONTEXT_LOCK_OWNER = "lockOwner";

    private WeeklyFamilyRecapJobConstants() {}
}
