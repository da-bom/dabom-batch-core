package com.template.worker.jobs.recap.monthly.model;

import java.util.LinkedHashMap;
import java.util.Map;

// 월 경계 partial week에서 보강한 사용량 집계값
public record MonthlyUsageSupplementMetrics(
        long totalUsedBytes,
        Map<String, Long> usageBytesByWeekday,
        MonthlyUsagePeakCandidate peakUsageCandidate) {

    public static MonthlyUsageSupplementMetrics empty() {
        return new MonthlyUsageSupplementMetrics(
                0L,
                new LinkedHashMap<>(
                        Map.of(
                                "monday", 0L,
                                "tuesday", 0L,
                                "wednesday", 0L,
                                "thursday", 0L,
                                "friday", 0L,
                                "saturday", 0L,
                                "sunday", 0L)),
                MonthlyUsagePeakCandidate.empty());
    }
}
