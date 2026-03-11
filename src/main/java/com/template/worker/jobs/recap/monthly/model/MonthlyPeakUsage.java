package com.template.worker.jobs.recap.monthly.model;

// 월간 피크 사용 구간 모델
public record MonthlyPeakUsage(int startHour, int endHour, String mostUsedWeekday) {}
