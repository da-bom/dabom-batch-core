package com.template.worker.jobs.recap.monthly.model;

// 월간 이의제기 요약 JSON 원본 모델
public record MonthlyAppealSummary(int totalAppeals, int approvedAppeals, int rejectedAppeals) {

    public static MonthlyAppealSummary empty() {
        return new MonthlyAppealSummary(0, 0, 0);
    }
}
