package com.template.worker.jobs.recap.weekly.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

@Component
public class WeekStartDateParameterSupport {

    public LocalDate resolveWeekStartDate(JobParameters jobParameters) {
        // 파라미터가 없으면 기본 주차를 사용
        if (jobParameters == null) {
            return defaultWeekStartDate();
        }

        String weekStartDate =
                jobParameters.getString(WeeklyFamilyRecapJobConstants.PARAM_WEEK_START_DATE);
        return resolveWeekStartDate(weekStartDate);
    }

    public LocalDate resolveWeekStartDate(String weekStartDate) {
        // 미지정 시 직전 주 월요일을 기본값으로 사용
        if (weekStartDate == null || weekStartDate.isBlank()) {
            return defaultWeekStartDate();
        }

        try {
            LocalDate parsedWeekStartDate = LocalDate.parse(weekStartDate);
            // 운영 파라미터 실수를 막기 위해 월요일만 허용
            if (!DayOfWeek.MONDAY.equals(parsedWeekStartDate.getDayOfWeek())) {
                throw new IllegalArgumentException(
                        "weekStartDate must be Monday. expected yyyy-MM-dd");
            }
            return parsedWeekStartDate;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid weekStartDate format. expected yyyy-MM-dd", exception);
        }
    }

    public LocalDate defaultWeekStartDate() {
        // 현재 주 월요일에서 1주를 빼 직전 주 시작일을 계산
        LocalDate today =
                ZonedDateTime.now(WeeklyFamilyRecapJobConstants.KST_ZONE_ID).toLocalDate();
        LocalDate thisWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return thisWeekMonday.minusWeeks(1);
    }
}
