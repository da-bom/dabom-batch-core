package com.template.worker.jobs.recap.weekly.model;

// 주간 피크 사용 구간 모델
public record WeeklyPeakUsage(int startHour, int endHour, long peakBytes) {}
