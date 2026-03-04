package com.template.worker.jobs.usagereset.tasklet;

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

import com.template.worker.global.util.RedisKeyGenerator;
import com.template.worker.jobs.usagereset.support.MonthlyResetLockManager;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyResetLockTasklet implements Tasklet, StepExecutionListener {

    private final MonthlyResetLockManager monthlyResetLockManager;
    private final MonthlyUsageResetJobParameterSupport parameterSupport;
    private final MonthlyUsageResetProperties properties;
    private final RedisKeyGenerator keyGenerator;

    private String lockKey;
    private String lockOwner;
    private LocalDate targetMonth;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // targetMonth 기준으로 락 키와 소유자 값을 준비함
        targetMonth = parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
        lockKey = keyGenerator.monthlyResetLockKey(targetMonth);
        lockOwner = UUID.randomUUID().toString();

        // 후속 스텝과 리스너에서 재사용할 값을 JobExecutionContext에 저장함
        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();
        jobContext.putString(
                MonthlyUsageResetJobConstants.JOB_CONTEXT_TARGET_MONTH, targetMonth.toString());
        jobContext.putString(MonthlyUsageResetJobConstants.JOB_CONTEXT_LOCK_KEY, lockKey);
        jobContext.putString(MonthlyUsageResetJobConstants.JOB_CONTEXT_LOCK_OWNER, lockOwner);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        boolean lockAcquired =
                monthlyResetLockManager.tryAcquire(lockKey, lockOwner, properties.getLockTtl());

        // 락 미획득 시 중복 실행을 피하기 위해 별도 종료 코드를 설정함
        if (!lockAcquired) {
            contribution.setExitStatus(
                    new ExitStatus(MonthlyUsageResetJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED));
            log.info(
                    "Skip job execution because lock is already held. lockKey={}, targetMonth={}",
                    lockKey,
                    targetMonth);
            return RepeatStatus.FINISHED;
        }

        // 락 획득 성공 시 다음 스텝 진행을 허용함
        log.info(
                "Acquired monthly usage reset lock. lockKey={}, targetMonth={}",
                lockKey,
                targetMonth);
        return RepeatStatus.FINISHED;
    }
}
