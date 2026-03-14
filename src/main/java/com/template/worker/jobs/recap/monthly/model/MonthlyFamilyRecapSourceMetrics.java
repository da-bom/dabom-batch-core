package com.template.worker.jobs.recap.monthly.model;

import java.util.List;

// 리포지토리에서 수집한 월간 리캡 원본 집계값 묶음
public record MonthlyFamilyRecapSourceMetrics(
        List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots,
        long totalQuotaBytes,
        MonthlyUsageSupplementMetrics partialUsageMetrics,
        MonthlyMissionSummary missionSummary,
        MonthlyAppealSummary appealSummary,
        int missionCarryInCount,
        int appealCarryInCount,
        MonthlyAppealHighlights appealHighlights) {}
