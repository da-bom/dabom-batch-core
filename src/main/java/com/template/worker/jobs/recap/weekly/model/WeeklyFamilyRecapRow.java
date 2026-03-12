package com.template.worker.jobs.recap.weekly.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// family_recap_weekly 업서트 대상 모델
public record WeeklyFamilyRecapRow(
        Long familyId,
        LocalDate weekStartDate,
        long totalUsedBytes,
        long totalQuotaBytes,
        BigDecimal usageRatePercent,
        String usageByWeekdayJson,
        String peakUsageJson,
        int missionCreatedCount,
        int missionCompletedCount,
        int missionRejectedCount,
        int totalAppealCount,
        int approvedAppealCount,
        int rejectedAppealCount) {}
