package com.template.worker.jobs.usagereset.support;

import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.TargetMonthParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyUsageResetJobParameterSupport {

    private final TargetMonthParameterSupport targetMonthParameterSupport;

    public LocalDate resolveTargetMonth(JobParameters jobParameters) {
        return targetMonthParameterSupport.resolveTargetMonth(
                jobParameters,
                MonthlyUsageResetJobConstants.PARAM_TARGET_MONTH,
                BatchJobConstants.KST_ZONE_ID);
    }

    public LocalDate resolveTargetMonth(String targetMonth) {
        return targetMonthParameterSupport.resolveTargetMonth(
                targetMonth, BatchJobConstants.KST_ZONE_ID);
    }

    public long resolveNextMonthStartEpochSecond(LocalDate targetMonth) {
        // 다음 달 시작 epoch second를 계산해 Redis EXPIREAT에 사용함
        return targetMonth
                .plusMonths(1)
                .atStartOfDay(BatchJobConstants.KST_ZONE_ID)
                .toEpochSecond();
    }
}
