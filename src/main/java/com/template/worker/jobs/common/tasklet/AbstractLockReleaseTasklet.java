package com.template.worker.jobs.common.tasklet;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractLockReleaseTasklet implements Tasklet, StepExecutionListener {

    private String lockKey;
    private String lockOwner;
    private ExecutionContext jobContext;

    @Override
    public final void beforeStep(StepExecution stepExecution) {
        // 락 해제 스텝에서 사용할 키와 소유자를 컨텍스트에서 복원함
        jobContext = stepExecution.getJobExecution().getExecutionContext();
        lockKey = jobContext.getString(lockKeyContextName(), null);
        lockOwner = jobContext.getString(lockOwnerContextName(), null);
    }

    @Override
    public final RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 소유자 일치 시에만 락을 삭제해 타 인스턴스 락 손상을 방지함
        boolean released = releaseIfOwner(lockKey, lockOwner);
        onLockReleased(jobContext, released);
        log.info("Release {} lock. lockKey={}, released={}", lockLogName(), lockKey, released);
        return RepeatStatus.FINISHED;
    }

    protected abstract boolean releaseIfOwner(String lockKey, String lockOwner);

    protected abstract String lockLogName();

    protected abstract String lockKeyContextName();

    protected abstract String lockOwnerContextName();

    protected void onLockReleased(ExecutionContext jobContext, boolean released) {}
}
