package com.template.worker.jobs.recap.monthly.support;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.TargetMonthParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapJobParameterSupport {

    private final TargetMonthParameterSupport targetMonthParameterSupport;

    public LocalDate resolveTargetMonth(JobParameters jobParameters) {
        // 수동 실행 등으로 파라미터 객체가 없으면 기본 대상 월을 사용
        if (jobParameters == null) {
            return defaultTargetMonth();
        }

        String targetMonth =
                jobParameters.getString(MonthlyFamilyRecapJobConstants.PARAM_TARGET_MONTH);
        return resolveTargetMonth(targetMonth);
    }

    public LocalDate resolveTargetMonth(String targetMonth) {
        // targetMonth 미지정 시 스케줄 기본 대상(직전 달)을 사용
        if (targetMonth == null || targetMonth.isBlank()) {
            return defaultTargetMonth();
        }

        return targetMonthParameterSupport.resolveTargetMonth(
                targetMonth, BatchJobConstants.KST_ZONE_ID);
    }

    public LocalDate defaultTargetMonth() {
        // 스케줄 실행 시점 기준 직전 달 월 시작일을 기본값으로 사용
        return ZonedDateTime.now(BatchJobConstants.KST_ZONE_ID)
                .toLocalDate()
                .withDayOfMonth(1)
                .minusMonths(1);
    }
}
