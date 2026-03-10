package com.template.worker.jobs.recap.weekly.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapLockKeyGenerator {

    // 배치 락 키 prefix
    private static final String BATCH_LOCK_PREFIX = "batch:lock:weekly-family-recap";

    private final TargetMonthLockKeyGenerator targetMonthLockKeyGenerator;

    public String weeklyFamilyRecapLockKey(LocalDate weekStartDate) {
        // 같은 주차 중복 실행을 막기 위해 주 시작일 포함 키를 생성
        return targetMonthLockKeyGenerator.targetMonthLockKey(BATCH_LOCK_PREFIX, weekStartDate);
    }
}
