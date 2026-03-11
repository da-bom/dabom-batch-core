package com.template.worker.jobs.recap.monthly.support;

import java.time.ZoneId;

public final class MonthlyFamilyRecapJobConstants {

    // 월간 리캡 배치 기준 타임존
    public static final String KST_ZONE_ID_NAME = "Asia/Seoul";
    public static final ZoneId KST_ZONE_ID = ZoneId.of(KST_ZONE_ID_NAME);

    // 잡/파라미터 식별자
    public static final String JOB_NAME = "monthly-family-recap-job";
    public static final String PARAM_TARGET_MONTH = "targetMonth";

    // 스텝 이름
    public static final String STEP_ACQUIRE_MONTHLY_RECAP_LOCK = "acquire-monthly-recap-lock-step";
    public static final String STEP_PROCESS_MONTHLY_RECAP = "process-monthly-recap-step";
    public static final String STEP_RELEASE_MONTHLY_RECAP_LOCK = "release-monthly-recap-lock-step";

    // 락 미획득 종료 코드
    public static final String EXIT_STATUS_LOCK_NOT_ACQUIRED = "LOCK_NOT_ACQUIRED";

    // 실행 컨텍스트 키
    public static final String JOB_CONTEXT_TARGET_MONTH = "targetMonth";
    public static final String JOB_CONTEXT_TARGET_MONTH_DEFAULT = "default";
    public static final String JOB_CONTEXT_LOCK_KEY = "lockKey";
    public static final String JOB_CONTEXT_LOCK_OWNER = "lockOwner";

    private MonthlyFamilyRecapJobConstants() {}
}
