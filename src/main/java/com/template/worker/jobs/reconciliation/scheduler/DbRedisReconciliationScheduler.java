package com.template.worker.jobs.reconciliation.scheduler;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "batch.schedules.db-redis-reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DbRedisReconciliationScheduler {

    private final BatchJobLauncher launcher;

    @Scheduled(
            cron = "${batch.schedules.db-redis-reconciliation.cron:0 0 3 * * *}",
            zone = DbRedisReconciliationJobConstants.KST_ZONE_ID_NAME)
    public void runDbRedisReconciliationJob() {
        Map<String, String> params = new HashMap<>();
        // 스케줄 실행 시점의 KST 월 시작일을 targetMonth로 전달
        String targetMonth =
                ZonedDateTime.now(DbRedisReconciliationJobConstants.KST_ZONE_ID)
                        .toLocalDate()
                        .withDayOfMonth(1)
                        .toString();
        params.put(DbRedisReconciliationJobConstants.PARAM_TARGET_MONTH, targetMonth);

        try {
            launcher.run(DbRedisReconciliationJobConstants.JOB_NAME, params);
        } catch (Exception exception) {
            // 스케줄 실패가 다음 실행을 막지 않도록 예외를 로그로만 처리
            log.error(
                    "Failed to run DB-Redis reconciliation job by scheduler. targetMonth={}",
                    targetMonth,
                    exception);
        }
    }
}
