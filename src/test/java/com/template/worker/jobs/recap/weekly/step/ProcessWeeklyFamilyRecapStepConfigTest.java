package com.template.worker.jobs.recap.weekly.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.common.retry.BatchRetrySupport;
import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapRow;
import com.template.worker.jobs.recap.weekly.processor.WeeklyFamilyRecapProcessor;
import com.template.worker.jobs.recap.weekly.reader.WeeklyFamilyRecapFamilyReader;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapProperties;
import com.template.worker.jobs.recap.weekly.writer.WeeklyFamilyRecapUpsertWriter;

class ProcessWeeklyFamilyRecapStepConfigTest {

    @Test
    @DisplayName("processWeeklyFamilyRecapStep - Deadlock 이 두 번 나도 세 번째에 성공한다")
    void processWeeklyFamilyRecapStep_retriesDbFailuresThenCompletes() throws Exception {
        JobRepository jobRepository = mock(JobRepository.class);
        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();
        WeeklyFamilyRecapFamilyReader reader = mock(WeeklyFamilyRecapFamilyReader.class);
        WeeklyFamilyRecapProcessor processor = mock(WeeklyFamilyRecapProcessor.class);
        WeeklyFamilyRecapUpsertWriter writer = mock(WeeklyFamilyRecapUpsertWriter.class);
        WeeklyFamilyRecapProperties properties = new WeeklyFamilyRecapProperties();
        properties.setChunkSize(2);

        ProcessWeeklyFamilyRecapStepConfig stepConfig =
                new ProcessWeeklyFamilyRecapStepConfig(
                        jobRepository,
                        transactionManager,
                        reader,
                        processor,
                        writer,
                        properties,
                        new BatchRetrySupport(3, 0L));

        when(reader.read()).thenReturn(10L, null);
        when(processor.process(10L)).thenReturn(createRow());

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new PessimisticLockingFailureException("deadlock", null);
                            }
                            return null;
                        })
                .when(writer)
                .write(any());

        Step step = stepConfig.processWeeklyFamilyRecapStep();
        StepExecution stepExecution = createStepExecution("process-weekly-family-recap-step");

        step.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("processWeeklyFamilyRecapStep - Deadlock 이 한도를 넘으면 FAILED 처리된다")
    void processWeeklyFamilyRecapStep_failsAfterRetryLimitExceeded() throws Exception {
        JobRepository jobRepository = mock(JobRepository.class);
        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();
        WeeklyFamilyRecapFamilyReader reader = mock(WeeklyFamilyRecapFamilyReader.class);
        WeeklyFamilyRecapProcessor processor = mock(WeeklyFamilyRecapProcessor.class);
        WeeklyFamilyRecapUpsertWriter writer = mock(WeeklyFamilyRecapUpsertWriter.class);
        WeeklyFamilyRecapProperties properties = new WeeklyFamilyRecapProperties();
        properties.setChunkSize(2);

        ProcessWeeklyFamilyRecapStepConfig stepConfig =
                new ProcessWeeklyFamilyRecapStepConfig(
                        jobRepository,
                        transactionManager,
                        reader,
                        processor,
                        writer,
                        properties,
                        new BatchRetrySupport(3, 0L));

        when(reader.read()).thenReturn(10L, null);
        when(processor.process(10L)).thenReturn(createRow());
        doThrow(new PessimisticLockingFailureException("deadlock", null)).when(writer).write(any());

        Step step = stepConfig.processWeeklyFamilyRecapStep();
        StepExecution stepExecution = createStepExecution("process-weekly-family-recap-step");

        step.execute(stepExecution);

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    private WeeklyFamilyRecapRow createRow() {
        return new WeeklyFamilyRecapRow(
                10L,
                LocalDate.of(2026, 3, 9),
                100L,
                1000L,
                new BigDecimal("10.00"),
                "{}",
                "{}",
                1,
                1,
                0,
                1,
                1,
                0);
    }

    private StepExecution createStepExecution(String stepName) {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "weeklyFamilyRecapJob"),
                        new JobParametersBuilder()
                                .addString("weekStartDate", "2026-03-09")
                                .toJobParameters());
        return new StepExecution(stepName, jobExecution);
    }
}
