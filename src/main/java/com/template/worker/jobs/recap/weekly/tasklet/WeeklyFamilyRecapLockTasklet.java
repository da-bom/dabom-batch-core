package com.template.worker.jobs.recap.weekly.tasklet;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;
import com.template.worker.jobs.common.tasklet.AbstractTargetMonthLockTasklet;
import com.template.worker.jobs.recap.weekly.support.WeekStartDateParameterSupport;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapLockTasklet extends AbstractTargetMonthLockTasklet {

    private final BatchLockManager batchLockManager;
    private final WeekStartDateParameterSupport parameterSupport;
    private final WeeklyFamilyRecapProperties properties;
    private final TargetMonthLockKeyGenerator lockKeyGenerator;

    @Override
    protected LocalDate resolveTargetMonth(JobParameters jobParameters) {
        // job parameter에서 주 시작일을 해석
        return parameterSupport.resolveWeekStartDate(jobParameters);
    }

    @Override
    protected String generateLockKey(LocalDate targetMonth) {
        // 주 시작일 기반 락 키 생성
        return lockKeyGenerator.targetMonthLockKey(
                WeeklyFamilyRecapJobConstants.BATCH_LOCK_PREFIX, targetMonth);
    }

    @Override
    protected boolean tryAcquire(String lockKey, String lockOwner, Duration ttl) {
        // 실행 락 획득 시도
        return batchLockManager.tryAcquire(lockKey, lockOwner, ttl);
    }

    @Override
    protected Duration lockTtl() {
        return properties.getLockTtl();
    }

    @Override
    protected String lockNotAcquiredExitStatus() {
        return BatchJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED;
    }

    @Override
    protected String lockLogName() {
        return "weekly family recap";
    }

    @Override
    protected void initializeJobContext(
            ExecutionContext jobContext, LocalDate targetMonth, String lockKey, String lockOwner) {
        // 후속 스텝과 리스너에서 쓸 컨텍스트 값을 저장
        jobContext.putString(
                WeeklyFamilyRecapJobConstants.JOB_CONTEXT_WEEK_START_DATE, targetMonth.toString());
        jobContext.putString(BatchJobConstants.JOB_CONTEXT_LOCK_KEY, lockKey);
        jobContext.putString(BatchJobConstants.JOB_CONTEXT_LOCK_OWNER, lockOwner);
    }
}
