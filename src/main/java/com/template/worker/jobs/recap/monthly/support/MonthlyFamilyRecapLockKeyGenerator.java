package com.template.worker.jobs.recap.monthly.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapLockKeyGenerator {

    // 배치 락 키 prefix
    private static final String BATCH_LOCK_PREFIX = "batch:lock:monthly-family-recap";

    private final TargetMonthLockKeyGenerator targetMonthLockKeyGenerator;

    public String monthlyFamilyRecapLockKey(LocalDate targetMonth) {
        // 같은 월 중복 실행을 막기 위해 targetMonth 포함 키를 생성
        return targetMonthLockKeyGenerator.targetMonthLockKey(BATCH_LOCK_PREFIX, targetMonth);
    }
}
