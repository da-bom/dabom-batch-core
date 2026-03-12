package com.template.worker.jobs.recap.weekly.model;

import java.util.Map;

// 리포지토리 집계 원본 값을 담는 모델
public record WeeklyFamilyRecapSourceMetrics(
        long totalUsedBytes,
        long totalQuotaBytes,
        Map<String, Long> usageBytesByWeekday,
        WeeklyPeakUsage peakUsage,
        int missionCreatedCount,
        int missionCompletedCount,
        int missionRejectedCount,
        int totalAppealCount,
        int approvedAppealCount,
        int rejectedAppealCount) {}
