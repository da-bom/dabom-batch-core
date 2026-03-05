package com.template.worker.jobs.usagereset.support;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

@Component
public class MonthlyUsageResetJobParameterSupport {

    public LocalDate resolveTargetMonth(JobParameters jobParameters) {
        // 파라미터 객체가 없으면 KST 현재 월 1일을 기본값으로 사용함
        if (jobParameters == null) {
            return defaultTargetMonth();
        }

        String targetMonth =
                jobParameters.getString(MonthlyUsageResetJobConstants.PARAM_TARGET_MONTH);
        return resolveTargetMonth(targetMonth);
    }

    public LocalDate resolveTargetMonth(String targetMonth) {
        // targetMonth 미지정 시 KST 현재 월 1일을 기본값으로 사용함
        if (targetMonth == null || targetMonth.isBlank()) {
            return defaultTargetMonth();
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

    public long resolveNextMonthStartEpochSecond(LocalDate targetMonth) {
        // 다음 달 시작 epoch second를 계산해 Redis EXPIREAT에 사용함
        return targetMonth
                .plusMonths(1)
                .atStartOfDay(MonthlyUsageResetJobConstants.KST_ZONE_ID)
                .toEpochSecond();
    }

    private LocalDate defaultTargetMonth() {
        return ZonedDateTime.now(MonthlyUsageResetJobConstants.KST_ZONE_ID)
                .toLocalDate()
                .withDayOfMonth(1);
    }
}
