package com.template.worker.jobs.usageprecreate.listener;

import java.util.Objects;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyUsagePrecreateJobListener implements JobExecutionListener {

    private final BatchLockManager batchLockManager;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 이 리스너는 잡 시작 전 선행 작업이 없고 종료 후 요약과 락 정리만 담당
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // JobExecutionContext에서 선생성 결과와 락 정보를 읽어 요약 로그를 남김
        ExecutionContext jobContext = jobExecution.getExecutionContext();

        String targetMonthFromParam =
                Objects.requireNonNullElse(
                        jobExecution
                                .getJobParameters()
                                .getString(MonthlyUsagePrecreateJobConstants.PARAM_TARGET_MONTH),
                        MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_TARGET_MONTH_DEFAULT);
        String targetMonth =
                jobContext.getString(
                        MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_TARGET_MONTH,
                        targetMonthFromParam);

        long precreatedCustomerQuotaCount =
                jobContext.getLong(
                        MonthlyUsagePrecreateJobConstants
                                .JOB_CONTEXT_PRECREATED_CUSTOMER_QUOTA_COUNT,
                        0L);
        long precreatedFamilyQuotaCount =
                jobContext.getLong(
                        MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_PRECREATED_FAMILY_QUOTA_COUNT,
                        0L);
        long skippedFamilyQuotaCount =
                jobContext.getLong(
                        MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_SKIPPED_FAMILY_QUOTA_COUNT,
                        0L);

        int failureCount = jobExecution.getAllFailureExceptions().size();
        log.info(
                "Monthly quota precreate summary. targetMonth={}, precreatedCustomerQuotaCount={},"
                    + " precreatedFamilyQuotaCount={}, skippedFamilyQuotaCount={}, failureCount={},"
                    + " status={}",
                targetMonth,
                precreatedCustomerQuotaCount,
                precreatedFamilyQuotaCount,
                skippedFamilyQuotaCount,
                failureCount,
                jobExecution.getStatus());

        // 예외 경로 누수 방지를 위해 최종 락 정리를 한 번 더 수행
        String lockKey = jobContext.getString(BatchJobConstants.JOB_CONTEXT_LOCK_KEY, null);
        String lockOwner = jobContext.getString(BatchJobConstants.JOB_CONTEXT_LOCK_OWNER, null);
        try {
            boolean released = batchLockManager.releaseIfOwner(lockKey, lockOwner);
            if (lockKey != null) {
                log.info(
                        "Monthly usage precreate final lock cleanup. lockKey={}, released={}",
                        lockKey,
                        released);
            }
        } catch (Exception exception) {
            log.error(
                    "Monthly usage precreate final lock cleanup failed. lockKey={}",
                    lockKey,
                    exception);
        }
    }
}
