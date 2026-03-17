package com.template.worker.jobs.recap.monthly.scheduler;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class MonthlyFamilyRecapSchedulerTest {

    @Mock private BatchJobLauncher launcher;
    @Mock private BatchAlertService batchAlertService;
    @Mock private MonthlyFamilyRecapJobParameterSupport parameterSupport;

    @Test
    @DisplayName("runMonthlyFamilyRecapJob - launcher 예외가 나면 Slack 알람을 보낸다")
    void runMonthlyFamilyRecapJob_sendsAlertWhenLauncherThrows() throws Exception {
        when(parameterSupport.defaultTargetMonth()).thenReturn(LocalDate.of(2026, 3, 1));
        MonthlyFamilyRecapScheduler scheduler =
                new MonthlyFamilyRecapScheduler(launcher, batchAlertService, parameterSupport);
        doThrow(new RuntimeException("boom"))
                .when(launcher)
                .run(eq(MonthlyFamilyRecapJobConstants.JOB_NAME), anyMap());

        scheduler.runMonthlyFamilyRecapJob();

        verify(batchAlertService)
                .sendSchedulerFailureAlert(
                        "monthly-family-recap-scheduler", "targetMonth=2026-03-01", "boom");
    }
}
