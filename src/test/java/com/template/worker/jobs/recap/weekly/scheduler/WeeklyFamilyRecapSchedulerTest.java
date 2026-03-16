package com.template.worker.jobs.recap.weekly.scheduler;

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
import com.template.worker.jobs.recap.weekly.support.WeekStartDateParameterSupport;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;

@ExtendWith(MockitoExtension.class)
class WeeklyFamilyRecapSchedulerTest {

    @Mock private BatchJobLauncher launcher;
    @Mock private BatchAlertService batchAlertService;
    @Mock private WeekStartDateParameterSupport parameterSupport;

    @Test
    @DisplayName("runWeeklyFamilyRecapJob - launcher 예외가 나면 Slack 알람을 보낸다")
    void runWeeklyFamilyRecapJob_sendsAlertWhenLauncherThrows() throws Exception {
        when(parameterSupport.defaultWeekStartDate()).thenReturn(LocalDate.of(2026, 3, 9));
        WeeklyFamilyRecapScheduler scheduler =
                new WeeklyFamilyRecapScheduler(launcher, batchAlertService, parameterSupport);
        doThrow(new RuntimeException("boom"))
                .when(launcher)
                .run(eq(WeeklyFamilyRecapJobConstants.JOB_NAME), anyMap());

        scheduler.runWeeklyFamilyRecapJob();

        verify(batchAlertService)
                .sendSchedulerFailureAlert(
                        "weekly-family-recap-scheduler", "weekStartDate=2026-03-09", "boom");
    }
}
