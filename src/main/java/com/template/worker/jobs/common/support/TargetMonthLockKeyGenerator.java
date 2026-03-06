package com.template.worker.jobs.common.support;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

@Component
public class TargetMonthLockKeyGenerator {

    public String targetMonthLockKey(String lockPrefix, LocalDate targetMonth) {
        // 월별 단일 실행 보장을 위해 targetMonth를 락 키에 포함함
        return lockPrefix + ":" + targetMonth;
    }
}
