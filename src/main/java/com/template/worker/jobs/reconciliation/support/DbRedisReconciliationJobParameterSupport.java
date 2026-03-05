package com.template.worker.jobs.reconciliation.support;

import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.TargetMonthParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DbRedisReconciliationJobParameterSupport {

    private final TargetMonthParameterSupport targetMonthParameterSupport;

    public LocalDate resolveTargetMonth(JobParameters jobParameters) {
        return targetMonthParameterSupport.resolveTargetMonth(
                jobParameters,
                DbRedisReconciliationJobConstants.PARAM_TARGET_MONTH,
                DbRedisReconciliationJobConstants.KST_ZONE_ID);
    }

    public LocalDate resolveTargetMonth(String targetMonth) {
        return targetMonthParameterSupport.resolveTargetMonth(
                targetMonth, DbRedisReconciliationJobConstants.KST_ZONE_ID);
    }
}
