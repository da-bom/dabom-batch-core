package com.template.worker.jobs.recap.monthly.model;

import java.time.LocalDate;

// 월간 집계 시 재사용하는 주간 리캡 스냅샷
public record MonthlyWeeklyRecapSnapshot(
        LocalDate weekStartDate,
        long totalUsedBytes,
        long totalQuotaBytes,
        String usageByWeekdayJson,
        String peakUsageJson,
        int missionCreatedCount,
        int missionCompletedCount,
        int missionRejectedCount) {}
