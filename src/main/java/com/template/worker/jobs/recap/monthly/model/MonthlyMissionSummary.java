package com.template.worker.jobs.recap.monthly.model;

// 월간 미션 요약 JSON 원본 모델
public record MonthlyMissionSummary(
        int totalMissionCount, int completedMissionCount, int rejectedRequestCount) {

    public static MonthlyMissionSummary empty() {
        return new MonthlyMissionSummary(0, 0, 0);
    }
}
