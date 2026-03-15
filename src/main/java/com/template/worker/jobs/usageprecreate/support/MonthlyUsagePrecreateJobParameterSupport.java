package com.template.worker.jobs.usageprecreate.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.TargetMonthParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyUsagePrecreateJobParameterSupport {

    private final TargetMonthParameterSupport targetMonthParameterSupport;
    private final Clock clock;

    public LocalDate resolveTargetMonth(JobParameters jobParameters) {
        if (jobParameters == null) {
            return defaultTargetMonth();
        }

        String targetMonth =
                jobParameters.getString(MonthlyUsagePrecreateJobConstants.PARAM_TARGET_MONTH);
        return resolveTargetMonth(targetMonth);
    }

    public LocalDate resolveTargetMonth(String targetMonth) {
        if (targetMonth == null || targetMonth.isBlank()) {
            return defaultTargetMonth();
        }

        return targetMonthParameterSupport.resolveTargetMonth(
                targetMonth, MonthlyUsagePrecreateJobConstants.KST_ZONE_ID);
    }

    public LocalDate defaultTargetMonth() {
        return LocalDate.now(clock).withDayOfMonth(1).plusMonths(1);
    }

    public boolean isLastDayOfMonth() {
        LocalDate today = LocalDate.now(clock);
        return today.equals(today.with(TemporalAdjusters.lastDayOfMonth()));
    }
}
