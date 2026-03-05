package com.template.worker.jobs.reconciliation.tasklet;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationLockManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationLockReleaseTasklet implements Tasklet, StepExecutionListener {

    private final DbRedisReconciliationLockManager lockManager;

    private String lockKey;
    private String lockOwner;
    private ExecutionContext jobContext;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // 락 해제 스텝에서 사용할 키와 소유자를 컨텍스트에서 복원함
        jobContext = stepExecution.getJobExecution().getExecutionContext();
        lockKey =
                jobContext.getString(DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_KEY, null);
        lockOwner =
                jobContext.getString(
                        DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_OWNER, null);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 소유자 일치 시에만 락을 삭제해 타 인스턴스 락 손상을 방지함
        boolean released = lockManager.releaseIfOwner(lockKey, lockOwner);
        jobContext.putString(
                DbRedisReconciliationJobConstants.JOB_CONTEXT_LOCK_RELEASED,
                String.valueOf(released));
        log.info("Release reconciliation lock. lockKey={}, released={}", lockKey, released);
        return RepeatStatus.FINISHED;
    }
}
