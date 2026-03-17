package com.template.worker.jobs.reconciliation.scheduler;

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

import com.template.worker.common.alert.BatchAlertService;
import com.template.worker.common.launcher.BatchJobLauncher;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;

@ExtendWith(MockitoExtension.class)
class DbRedisReconciliationSchedulerTest {

    @Mock private BatchJobLauncher launcher;
    @Mock private BatchAlertService batchAlertService;

    @Test
    @DisplayName("runDbRedisReconciliationJob - launcher 예외가 나면 Slack 알람을 보낸다")
    void runDbRedisReconciliationJob_sendsAlertWhenLauncherThrows() throws Exception {
        DbRedisReconciliationScheduler scheduler =
                new DbRedisReconciliationScheduler(launcher, batchAlertService);
        doThrow(new RuntimeException("boom"))
                .when(launcher)
                .run(eq(DbRedisReconciliationJobConstants.JOB_NAME), anyMap());

        scheduler.runDbRedisReconciliationJob();

        verify(batchAlertService)
                .sendSchedulerFailureAlert(
                        eq("db-redis-reconciliation-scheduler"),
                        argThat(value -> value.startsWith("targetMonth=")),
                        eq("boom"));
    }
}
