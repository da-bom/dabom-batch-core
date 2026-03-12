package com.template.worker.jobs.recap.monthly.model;

import java.util.List;

// 리포지토리 집계 원본 값을 담는 모델
public record MonthlyFamilyRecapSourceMetrics(
        List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots,
        long totalQuotaBytes,
        int totalAppeals,
        int approvedAppeals,
        int rejectedAppeals) {}
