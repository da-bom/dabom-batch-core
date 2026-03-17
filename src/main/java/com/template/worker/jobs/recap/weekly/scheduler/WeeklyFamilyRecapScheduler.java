package com.template.worker.jobs.recap.weekly.scheduler;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.recap.weekly.support.WeekStartDateParameterSupport;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "batch.schedules.weekly-family-recap.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WeeklyFamilyRecapScheduler {

    private final BatchJobLauncher launcher;
    private final BatchAlertService batchAlertService;
    private final WeekStartDateParameterSupport parameterSupport;

    @Scheduled(
            cron = "${batch.schedules.weekly-family-recap.cron:0 10 0 * * MON}",
            zone = BatchJobConstants.KST_ZONE_ID_NAME)
    public void runWeeklyFamilyRecapJob() {
        // 스케줄 실행 시점 기준 직전 주 월요일을 기본 파라미터로 전달
        String weekStartDate = parameterSupport.defaultWeekStartDate().toString();
        Map<String, String> params =
                Map.of(WeeklyFamilyRecapJobConstants.PARAM_WEEK_START_DATE, weekStartDate);

        try {
            launcher.run(WeeklyFamilyRecapJobConstants.JOB_NAME, params);
        } catch (Exception exception) {
            // 스케줄 실패가 다음 실행까지 전파되지 않도록 로그로만 기록
            log.error(
                    "Failed to run weekly family recap job by scheduler. weekStartDate={}",
                    weekStartDate,
                    exception);
            batchAlertService.sendSchedulerFailureAlert(
                    "weekly-family-recap-scheduler",
                    "weekStartDate=" + weekStartDate,
                    exception.getMessage());
        }
    }
}
