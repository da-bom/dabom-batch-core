package com.template.worker.jobs.recap.monthly.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobParameterSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "batch.schedules.monthly-family-recap.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MonthlyFamilyRecapScheduler {

    private final BatchJobLauncher launcher;
    private final BatchAlertService batchAlertService;
    private final MonthlyFamilyRecapJobParameterSupport parameterSupport;

    @Scheduled(
            cron = "${batch.schedules.monthly-family-recap.cron:0 20 0 1 * *}",
            zone = MonthlyFamilyRecapJobConstants.KST_ZONE_ID_NAME)
    public void runMonthlyFamilyRecapJob() {
        Map<String, String> params = new HashMap<>();
        // 스케줄 실행 시점 기준 직전 달 시작일을 기본 파라미터로 전달
        String targetMonth = parameterSupport.defaultTargetMonth().toString();
        params.put(MonthlyFamilyRecapJobConstants.PARAM_TARGET_MONTH, targetMonth);

        try {
            launcher.run(MonthlyFamilyRecapJobConstants.JOB_NAME, params);
        } catch (Exception exception) {
            // 스케줄 실패가 다음 실행까지 전파되지 않도록 로그로만 기록
            log.error(
                    "Failed to run monthly family recap job by scheduler. targetMonth={}",
                    targetMonth,
                    exception);
            batchAlertService.sendSchedulerFailureAlert(
                    "monthly-family-recap-scheduler",
                    "targetMonth=" + targetMonth,
                    exception.getMessage());
        }
    }
}
