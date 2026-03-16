package com.template.worker.jobs.usageprecreate.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobParameterSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "batch.schedules.monthly-usage-precreate.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MonthlyUsagePrecreateScheduler {

    private final BatchJobLauncher launcher;
    private final BatchAlertService batchAlertService;
    private final MonthlyUsagePrecreateJobParameterSupport parameterSupport;

    @Scheduled(
            cron = "${batch.schedules.monthly-usage-precreate.cron:0 30 23 28-31 * *}",
            zone = MonthlyUsagePrecreateJobConstants.KST_ZONE_ID_NAME)
    public void runMonthlyUsagePrecreateJob() {
        // cron이 28~31일에 걸리므로 실제 월말 여부를 한 번 더 검증
        if (!parameterSupport.isLastDayOfMonth()) {
            log.info("Skip monthly usage precreate because today is not the last day of month");
            return;
        }

        Map<String, String> params = new HashMap<>();
        // 월말 23:30에 다음 달 1일을 targetMonth로 전달
        String targetMonth = parameterSupport.defaultTargetMonth().toString();
        params.put(MonthlyUsagePrecreateJobConstants.PARAM_TARGET_MONTH, targetMonth);

        try {
            launcher.run(MonthlyUsagePrecreateJobConstants.JOB_NAME, params);
        } catch (Exception exception) {
            // 스케줄 실패가 다음 실행을 막지 않도록 예외를 로그로만 처리
            log.error(
                    "Failed to run monthly usage precreate job by scheduler. targetMonth={}",
                    targetMonth,
                    exception);
            batchAlertService.sendSchedulerFailureAlert(
                    "monthly-usage-precreate-scheduler",
                    "targetMonth=" + targetMonth,
                    exception.getMessage());
        }
    }
}
