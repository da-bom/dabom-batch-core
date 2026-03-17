package com.template.worker.jobs.usagereset.scheduler;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.template.worker.global.alert.BatchAlertService;
import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;

@ExtendWith(MockitoExtension.class)
class MonthlyUsageResetSchedulerTest {

    @Mock private BatchJobLauncher launcher;
    @Mock private BatchAlertService batchAlertService;

    @Test
    @DisplayName("runMonthlyUsageResetJob - launcher 예외가 나면 Slack 알람을 보낸다")
    void runMonthlyUsageResetJob_sendsAlertWhenLauncherThrows() throws Exception {
        MonthlyUsageResetScheduler scheduler =
                new MonthlyUsageResetScheduler(launcher, batchAlertService);
        doThrow(new RuntimeException("boom"))
                .when(launcher)
                .run(eq(MonthlyUsageResetJobConstants.JOB_NAME), anyMap());

        scheduler.runMonthlyUsageResetJob();

        verify(batchAlertService)
                .sendSchedulerFailureAlert(
                        eq("monthly-usage-reset-scheduler"),
                        argThat(value -> value.startsWith("targetMonth=")),
                        eq("boom"));
    }
}
