package com.template.worker.jobs.common.support;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

@Component
public class TargetMonthParameterSupport {

    public LocalDate resolveTargetMonth(
            JobParameters jobParameters, String parameterName, ZoneId zoneId) {
        // 파라미터 객체가 없으면 해당 타임존의 현재 월 1일을 기본값으로 사용함
        if (jobParameters == null) {
            return defaultTargetMonth(zoneId);
        }

        String targetMonth = jobParameters.getString(parameterName);
        return resolveTargetMonth(targetMonth, zoneId);
    }

    public LocalDate resolveTargetMonth(String targetMonth, ZoneId zoneId) {
        // targetMonth 미지정 시 해당 타임존의 현재 월 1일을 기본값으로 사용함
        if (targetMonth == null || targetMonth.isBlank()) {
            return defaultTargetMonth(zoneId);
        }

        try {
            // yyyy-MM-01 형식을 강제해 운영 파라미터 오류를 조기 차단함
            LocalDate parsedTargetMonth = LocalDate.parse(targetMonth);
            if (parsedTargetMonth.getDayOfMonth() != 1) {
                throw new IllegalArgumentException(
                        "targetMonth must be first day of month. expected yyyy-MM-01");
            }
            return parsedTargetMonth;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid targetMonth format. expected yyyy-MM-01", exception);
        }
    }

    private LocalDate defaultTargetMonth(ZoneId zoneId) {
        ZoneId targetZone = Objects.requireNonNull(zoneId);
        return ZonedDateTime.now(targetZone).toLocalDate().withDayOfMonth(1);
    }
}
