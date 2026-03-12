package com.template.worker.jobs.recap.monthly.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobParameterSupport;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapLockKeyGenerator;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapLockManager;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapProperties;

@ExtendWith(MockitoExtension.class)
class MonthlyFamilyRecapLockTaskletTest {

    @Mock private MonthlyFamilyRecapLockManager lockManager;
    @Mock private MonthlyFamilyRecapJobParameterSupport parameterSupport;
    @Mock private MonthlyFamilyRecapProperties properties;
    @Mock private MonthlyFamilyRecapLockKeyGenerator lockKeyGenerator;

    @InjectMocks private MonthlyFamilyRecapLockTasklet tasklet;

    @Test
    @DisplayName("execute - 락 미획득 시 LOCK_NOT_ACQUIRED로 종료한다")
    void execute_lockNotAcquired_setsLockNotAcquiredExitStatus() {
        JobParameters jobParameters =
                new JobParametersBuilder().addString("targetMonth", "2026-03-01").toJobParameters();
        JobInstance jobInstance = new JobInstance(1L, "monthly-family-recap-job");
        JobExecution jobExecution = new JobExecution(jobInstance, jobParameters);
        StepExecution stepExecution = new StepExecution("acquire-lock-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(lockKeyGenerator.monthlyFamilyRecapLockKey(targetMonth))
                .thenReturn("batch:lock:monthly-family-recap:2026-03-01");
        when(properties.getLockTtl()).thenReturn(Duration.ofHours(1));
        when(lockManager.tryAcquire(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        tasklet.beforeStep(stepExecution);

        StepContribution contribution = new StepContribution(stepExecution);
        ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

        tasklet.execute(contribution, chunkContext);

        assertThat(contribution.getExitStatus().getExitCode())
                .isEqualTo(MonthlyFamilyRecapJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED);
        assertThat(
                        jobExecution
                                .getExecutionContext()
                                .getString(MonthlyFamilyRecapJobConstants.JOB_CONTEXT_TARGET_MONTH))
                .isEqualTo("2026-03-01");
    }
}
