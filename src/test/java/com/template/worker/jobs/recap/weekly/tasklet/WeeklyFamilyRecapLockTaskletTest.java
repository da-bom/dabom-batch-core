package com.template.worker.jobs.recap.weekly.tasklet;

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

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.BatchLockManager;
import com.template.worker.jobs.common.support.TargetMonthLockKeyGenerator;
import com.template.worker.jobs.recap.weekly.support.WeekStartDateParameterSupport;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapJobConstants;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapProperties;

@ExtendWith(MockitoExtension.class)
class WeeklyFamilyRecapLockTaskletTest {

    @Mock private BatchLockManager batchLockManager;
    @Mock private WeekStartDateParameterSupport parameterSupport;
    @Mock private WeeklyFamilyRecapProperties properties;
    @Mock private TargetMonthLockKeyGenerator lockKeyGenerator;

    @InjectMocks private WeeklyFamilyRecapLockTasklet tasklet;

    @Test
    @DisplayName("execute - 락 미획득 시 LOCK_NOT_ACQUIRED로 종료한다")
    void execute_lockNotAcquired_setsLockNotAcquiredExitStatus() {
        JobParameters jobParameters =
                new JobParametersBuilder()
                        .addString("weekStartDate", "2026-03-02")
                        .toJobParameters();
        JobInstance jobInstance = new JobInstance(1L, "weekly-family-recap-job");
        JobExecution jobExecution = new JobExecution(jobInstance, jobParameters);
        StepExecution stepExecution = new StepExecution("acquire-lock-step", jobExecution);

        LocalDate weekStartDate = LocalDate.of(2026, 3, 2);
        when(parameterSupport.resolveWeekStartDate(any(JobParameters.class)))
                .thenReturn(weekStartDate);
        when(lockKeyGenerator.targetMonthLockKey(
                        WeeklyFamilyRecapJobConstants.BATCH_LOCK_PREFIX, weekStartDate))
                .thenReturn("batch:lock:weekly-family-recap:2026-03-02");
        when(properties.getLockTtl()).thenReturn(Duration.ofHours(1));
        when(batchLockManager.tryAcquire(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        tasklet.beforeStep(stepExecution);

        StepContribution contribution = new StepContribution(stepExecution);
        ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

        tasklet.execute(contribution, chunkContext);

        assertThat(contribution.getExitStatus().getExitCode())
                .isEqualTo(BatchJobConstants.EXIT_STATUS_LOCK_NOT_ACQUIRED);
        assertThat(
                        jobExecution
                                .getExecutionContext()
                                .getString(
                                        WeeklyFamilyRecapJobConstants.JOB_CONTEXT_WEEK_START_DATE))
                .isEqualTo("2026-03-02");
    }
}
