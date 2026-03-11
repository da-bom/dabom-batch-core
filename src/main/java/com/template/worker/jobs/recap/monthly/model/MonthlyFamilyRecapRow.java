package com.template.worker.jobs.recap.monthly.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// family_recap_monthly 업서트 대상 모델
public record MonthlyFamilyRecapRow(
        Long familyId,
        LocalDate reportMonth,
        long totalUsedBytes,
        long totalQuotaBytes,
        BigDecimal usageRatePercent,
        String usageByWeekdayJson,
        String peakUsageJson,
        String missionSummaryJson,
        String appealSummaryJson,
        String appealHighlightsJson,
        BigDecimal communicationScore) {}
