package com.template.worker.jobs.usagereset.scheduler;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "batch.schedules.monthly-usage-reset.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MonthlyUsageResetScheduler {

    private final BatchJobLauncher launcher;
    private final BatchAlertService batchAlertService;

    @Scheduled(
            cron = "${batch.schedules.monthly-usage-reset.cron:0 1 0 1 * *}",
            zone = MonthlyUsageResetJobConstants.KST_ZONE_ID_NAME)
    public void runMonthlyUsageResetJob() {
        Map<String, String> params = new HashMap<>();
        // 스케줄 실행 시점의 KST 월 시작일을 targetMonth로 전달
        String targetMonth =
                ZonedDateTime.now(MonthlyUsageResetJobConstants.KST_ZONE_ID)
                        .toLocalDate()
                        .withDayOfMonth(1)
                        .toString();
        params.put(MonthlyUsageResetJobConstants.PARAM_TARGET_MONTH, targetMonth);

        try {
            launcher.run(MonthlyUsageResetJobConstants.JOB_NAME, params);
        } catch (Exception exception) {
            // 스케줄 실패가 다음 실행을 막지 않도록 예외를 로그로만 처리
            log.error(
                    "Failed to run monthly usage reset job by scheduler. targetMonth={}",
                    targetMonth,
                    exception);
            batchAlertService.sendSchedulerFailureAlert(
                    "monthly-usage-reset-scheduler",
                    "targetMonth=" + targetMonth,
                    exception.getMessage());
        }
    }
}
