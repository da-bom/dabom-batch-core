package com.template.worker.jobs.recap.weekly.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapRow;
import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.weekly.model.WeeklyPeakUsage;
import com.template.worker.jobs.recap.weekly.query.WeeklyFamilyRecapAggregationRepository;
import com.template.worker.jobs.recap.weekly.support.WeekStartDateParameterSupport;

@ExtendWith(MockitoExtension.class)
class WeeklyFamilyRecapProcessorTest {

    @Mock private WeeklyFamilyRecapAggregationRepository aggregationRepository;
    @Mock private WeekStartDateParameterSupport parameterSupport;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private WeeklyFamilyRecapProcessor processor;

    @Test
    @DisplayName("process - 주간 집계 결과를 리캡 row로 변환한다")
    void process_buildsWeeklyRecapRow() {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "weekly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("weekStartDate", "2026-03-02")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-weekly-recap-step", jobExecution);

        LocalDate weekStartDate = LocalDate.of(2026, 3, 2);
        when(parameterSupport.resolveWeekStartDate(any(JobParameters.class)))
                .thenReturn(weekStartDate);

        Map<String, Long> usageBytesByWeekday = new LinkedHashMap<>();
        usageBytesByWeekday.put("monday", 200L);
        usageBytesByWeekday.put("tuesday", 300L);
        usageBytesByWeekday.put("wednesday", 500L);

        WeeklyFamilyRecapSourceMetrics sourceMetrics =
                new WeeklyFamilyRecapSourceMetrics(
                        1000L,
                        4000L,
                        usageBytesByWeekday,
                        new WeeklyPeakUsage(21, 22, 500L),
                        3,
                        2,
                        1,
                        4);

        when(aggregationRepository.aggregate(10L, weekStartDate)).thenReturn(sourceMetrics);

        processor.beforeStep(stepExecution);
        WeeklyFamilyRecapRow row = processor.process(10L);

        assertThat(row.familyId()).isEqualTo(10L);
        assertThat(row.weekStartDate()).isEqualTo(weekStartDate);
        assertThat(row.totalUsedBytes()).isEqualTo(1000L);
        assertThat(row.totalQuotaBytes()).isEqualTo(4000L);
        assertThat(row.usageRatePercent()).isEqualTo(new BigDecimal("25.00"));
        assertThat(row.usageByWeekdayJson())
                .isEqualTo(
                        "{\"monday\":20.00,\"tuesday\":30.00,\"wednesday\":50.00,"
                                + "\"thursday\":0.00,\"friday\":0.00,\"saturday\":0.00,"
                                + "\"sunday\":0.00}");
        assertThat(row.peakUsageJson())
                .isEqualTo("{\"startHour\":21,\"endHour\":22,\"peakBytes\":500}");
        assertThat(row.missionCreatedCount()).isEqualTo(3);
        assertThat(row.missionCompletedCount()).isEqualTo(2);
        assertThat(row.missionRejectedCount()).isEqualTo(1);
        assertThat(row.appealCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("process - 집계값이 음수면 예외가 발생한다")
    void process_withNegativeAggregate_throwsException() {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "weekly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("weekStartDate", "2026-03-02")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-weekly-recap-step", jobExecution);

        LocalDate weekStartDate = LocalDate.of(2026, 3, 2);
        when(parameterSupport.resolveWeekStartDate(any(JobParameters.class)))
                .thenReturn(weekStartDate);

        WeeklyFamilyRecapSourceMetrics sourceMetrics =
                new WeeklyFamilyRecapSourceMetrics(
                        -1L,
                        4000L,
                        Map.of("monday", 1L),
                        new WeeklyPeakUsage(0, 1, 1L),
                        0,
                        0,
                        0,
                        0);

        when(aggregationRepository.aggregate(10L, weekStartDate)).thenReturn(sourceMetrics);

        processor.beforeStep(stepExecution);

        assertThatThrownBy(() -> processor.process(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("process - JSON 직렬화에 실패하면 예외가 발생한다")
    void process_whenJsonSerializationFails_throwsException() throws Exception {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "weekly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("weekStartDate", "2026-03-02")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-weekly-recap-step", jobExecution);

        LocalDate weekStartDate = LocalDate.of(2026, 3, 2);
        when(parameterSupport.resolveWeekStartDate(any(JobParameters.class)))
                .thenReturn(weekStartDate);

        WeeklyFamilyRecapSourceMetrics sourceMetrics =
                new WeeklyFamilyRecapSourceMetrics(
                        1000L,
                        4000L,
                        Map.of("monday", 1000L),
                        new WeeklyPeakUsage(21, 22, 500L),
                        0,
                        0,
                        0,
                        0);
        when(aggregationRepository.aggregate(10L, weekStartDate)).thenReturn(sourceMetrics);

        doThrow(new JsonProcessingException("serialize fail") {})
                .when(objectMapper)
                .writeValueAsString(any());

        processor.beforeStep(stepExecution);

        assertThatThrownBy(() -> processor.process(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize")
                .hasMessageContaining("familyId=10");
    }
}
