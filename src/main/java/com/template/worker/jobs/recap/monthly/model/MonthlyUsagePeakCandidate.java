package com.template.worker.jobs.recap.monthly.model;

// 월간 peak 계산 시 full week / partial week 후보를 비교하기 위한 모델
public record MonthlyUsagePeakCandidate(int startHour, int endHour, long peakBytes) {

    public static MonthlyUsagePeakCandidate empty() {
        return new MonthlyUsagePeakCandidate(0, 1, 0L);
    }

    public boolean isBetterThan(MonthlyUsagePeakCandidate current) {
        if (peakBytes > current.peakBytes) {
            return true;
        }
        if (peakBytes < current.peakBytes) {
            return false;
        }
        if (startHour < current.startHour) {
            return true;
        }
        if (startHour > current.startHour) {
            return false;
        }
        return endHour < current.endHour;
    }
}
