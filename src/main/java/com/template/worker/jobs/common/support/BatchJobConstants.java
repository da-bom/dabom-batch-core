package com.template.worker.jobs.common.support;

import java.time.ZoneId;

public final class BatchJobConstants {

    public static final String KST_ZONE_ID_NAME = "Asia/Seoul";
    public static final ZoneId KST_ZONE_ID = ZoneId.of(KST_ZONE_ID_NAME);
    public static final String EXIT_STATUS_LOCK_NOT_ACQUIRED = "LOCK_NOT_ACQUIRED";
    public static final String JOB_CONTEXT_LOCK_KEY = "lockKey";
    public static final String JOB_CONTEXT_LOCK_OWNER = "lockOwner";

    private BatchJobConstants() {}
}
