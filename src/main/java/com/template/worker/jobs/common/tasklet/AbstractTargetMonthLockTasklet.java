package com.template.worker.jobs.common.tasklet;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTargetMonthLockTasklet implements Tasklet, StepExecutionListener {

    private String lockKey;
    private String lockOwner;
    private LocalDate targetMonth;
    private ExecutionContext jobContext;

    @Override
    public final void beforeStep(StepExecution stepExecution) {
        // targetMonth 기준으로 락 키와 소유자 값을 준비함
        targetMonth = resolveTargetMonth(stepExecution.getJobParameters());
        lockKey = generateLockKey(targetMonth);
        lockOwner = UUID.randomUUID().toString();

        // 후속 스텝/리스너에서 재사용할 값을 JobExecutionContext에 저장함
        jobContext = stepExecution.getJobExecution().getExecutionContext();
        initializeJobContext(jobContext, targetMonth, lockKey, lockOwner);
    }

    @Override
    public final RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        boolean lockAcquired = tryAcquire(lockKey, lockOwner, lockTtl());

        // 락 미획득 시 중복 실행을 피하기 위해 별도 종료 코드를 설정함
        if (!lockAcquired) {
            contribution.setExitStatus(new ExitStatus(lockNotAcquiredExitStatus()));
            onLockNotAcquired(jobContext);
            log.info(
                    "Skip {} job execution because lock is already held. lockKey={},"
                            + " targetMonth={}",
                    lockLogName(),
                    lockKey,
                    targetMonth);
            return RepeatStatus.FINISHED;
        }

        // 락 획득 성공 시 다음 스텝 진행을 허용함
        onLockAcquired(jobContext);
        log.info(
                "Acquired {} lock. lockKey={}, targetMonth={}",
                lockLogName(),
                lockKey,
                targetMonth);
        return RepeatStatus.FINISHED;
    }

    protected abstract LocalDate resolveTargetMonth(JobParameters jobParameters);

    protected abstract String generateLockKey(LocalDate targetMonth);

    protected abstract boolean tryAcquire(String lockKey, String lockOwner, Duration ttl);

    protected abstract Duration lockTtl();

    protected abstract String lockNotAcquiredExitStatus();

    protected abstract String lockLogName();

    protected abstract void initializeJobContext(
            ExecutionContext jobContext, LocalDate targetMonth, String lockKey, String lockOwner);

    protected void onLockAcquired(ExecutionContext jobContext) {}

    protected void onLockNotAcquired(ExecutionContext jobContext) {}
}
