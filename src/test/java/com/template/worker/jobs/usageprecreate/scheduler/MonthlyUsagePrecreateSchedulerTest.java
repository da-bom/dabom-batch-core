package com.template.worker.jobs.usageprecreate.scheduler;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.template.worker.global.launcher.BatchJobLauncher;
import com.template.worker.jobs.common.support.TargetMonthParameterSupport;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class MonthlyUsagePrecreateSchedulerTest {

    @Mock private BatchJobLauncher launcher;

    @Test
    @DisplayName("runMonthlyUsagePrecreateJob - 월말이면 다음 달 1일을 targetMonth로 실행한다")
    void runMonthlyUsagePrecreateJob_runsWithNextMonthTargetMonthOnLastDay() throws Exception {
        Clock clock =
                fixedClockAt(ZonedDateTime.of(2026, 3, 31, 23, 30, 0, 0, ZoneId.of("Asia/Seoul")));
        MonthlyUsagePrecreateJobParameterSupport parameterSupport =
                new MonthlyUsagePrecreateJobParameterSupport(
                        new TargetMonthParameterSupport(), clock);
        MonthlyUsagePrecreateScheduler scheduler =
                new MonthlyUsagePrecreateScheduler(launcher, parameterSupport);

        scheduler.runMonthlyUsagePrecreateJob();

        verify(launcher)
                .run(
                        MonthlyUsagePrecreateJobConstants.JOB_NAME,
                        Map.of(MonthlyUsagePrecreateJobConstants.PARAM_TARGET_MONTH, "2026-04-01"));
    }

    @Test
    @DisplayName("runMonthlyUsagePrecreateJob - 월말이 아니면 실행하지 않는다")
    void runMonthlyUsagePrecreateJob_skipsWhenTodayIsNotLastDay() throws Exception {
        Clock clock =
                fixedClockAt(ZonedDateTime.of(2026, 3, 30, 23, 30, 0, 0, ZoneId.of("Asia/Seoul")));
        MonthlyUsagePrecreateJobParameterSupport parameterSupport =
                new MonthlyUsagePrecreateJobParameterSupport(
                        new TargetMonthParameterSupport(), clock);
        MonthlyUsagePrecreateScheduler scheduler =
                new MonthlyUsagePrecreateScheduler(launcher, parameterSupport);

        scheduler.runMonthlyUsagePrecreateJob();

        verify(launcher, never()).run(anyString(), anyMap());
    }

    private Clock fixedClockAt(ZonedDateTime dateTime) {
        return Clock.fixed(Instant.from(dateTime), dateTime.getZone());
    }
}
