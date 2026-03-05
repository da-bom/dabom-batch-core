package com.template.worker.jobs.reconciliation.tasklet;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobParameterSupport;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationLockKeyGenerator;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationLockManager;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationLockTasklet implements Tasklet, StepExecutionListener {

    private final DbRedisReconciliationLockManager lockManager;
    private final DbRedisReconciliationJobParameterSupport parameterSupport;
    private final DbRedisReconciliationProperties properties;
    private final DbRedisReconciliationLockKeyGenerator lockKeyGenerator;

    private String lockKey;
    private String lockOwner;
    private LocalDate targetMonth;
    private ExecutionContext jobContext;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // targetMonth 기준으로 락 키와 소유자 값을 준비함
        targetMonth = parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
        lockKey = lockKeyGenerator.reconciliationLockKey(targetMonth);
        lockOwner = UUID.randomUUID().toString();

        // 후속 스텝/리스너에서 재사용할 값을 JobExecutionContext에 저장함
        jobContext = stepExecution.getJobExecution().getExecutionContext();
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_TARGET_MONTH, targetMonth.toString());
        jobContext.putString(DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_KEY, lockKey);
        jobContext.putString(DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_OWNER, lockOwner);
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_STATUS,
                DbRedisReconciliationJobConstants.LOCK_STATUS_NOT_ACQUIRED);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        boolean lockAcquired = lockManager.tryAcquire(lockKey, lockOwner, properties.getLockTtl());

        // 락 미획득 시 중복 실행을 피하기 위해 별도 종료 코드를 설정함
        if (!lockAcquired) {
            contribution.setExitStatus(
                    new ExitStatus(
                            DbRedisReconciliationJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED));
            log.info(
                    "Skip reconciliation job execution because lock is already held. lockKey={},"
                            + " targetMonth={}",
                    lockKey,
                    targetMonth);
            return RepeatStatus.FINISHED;
        }

        // 락 획득 성공 시 다음 스텝 진행을 허용함
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_STATUS,
                DbRedisReconciliationJobConstants.LOCK_STATUS_ACQUIRED);
        log.info("Acquired reconciliation lock. lockKey={}, targetMonth={}", lockKey, targetMonth);
        return RepeatStatus.FINISHED;
    }
}
