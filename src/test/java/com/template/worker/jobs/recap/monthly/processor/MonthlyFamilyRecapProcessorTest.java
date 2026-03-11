package com.template.worker.jobs.recap.monthly.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapRow;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyWeeklyRecapSnapshot;
import com.template.worker.jobs.recap.monthly.query.MonthlyFamilyRecapAggregationRepository;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class MonthlyFamilyRecapProcessorTest {

    @Mock private MonthlyFamilyRecapAggregationRepository aggregationRepository;
    @Mock private MonthlyFamilyRecapJobParameterSupport parameterSupport;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private MonthlyFamilyRecapProcessor processor;

    @Test
    @DisplayName("process - 월간 full week 집계 결과를 리캡 row로 변환한다")
    void process_buildsMonthlyRecapRow() throws Exception {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "monthly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-monthly-recap-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);

        List<MonthlyWeeklyRecapSnapshot> snapshots =
                List.of(
                        new MonthlyWeeklyRecapSnapshot(
                                LocalDate.of(2026, 3, 2),
                                1000L,
                                5000L,
                                "{\"monday\":100.00,\"tuesday\":0.00,\"wednesday\":0.00,"
                                        + "\"thursday\":0.00,\"friday\":0.00,\"saturday\":0.00,"
                                        + "\"sunday\":0.00}",
                                "{\"startHour\":21,\"endHour\":22,\"peakBytes\":500}",
                                2,
                                1,
                                1,
                                1),
                        new MonthlyWeeklyRecapSnapshot(
                                LocalDate.of(2026, 3, 9),
                                2000L,
                                5000L,
                                "{\"monday\":0.00,\"tuesday\":100.00,\"wednesday\":0.00,"
                                        + "\"thursday\":0.00,\"friday\":0.00,\"saturday\":0.00,"
                                        + "\"sunday\":0.00}",
                                "{\"startHour\":20,\"endHour\":21,\"peakBytes\":800}",
                                1,
                                1,
                                0,
                                2));

        when(aggregationRepository.aggregate(10L, targetMonth))
                .thenReturn(new MonthlyFamilyRecapSourceMetrics(snapshots, 10000L, 5, 3, 1));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(10L);

        assertThat(row.familyId()).isEqualTo(10L);
        assertThat(row.reportMonth()).isEqualTo(targetMonth);
        assertThat(row.totalUsedBytes()).isEqualTo(3000L);
        assertThat(row.totalQuotaBytes()).isEqualTo(10000L);
        assertThat(row.usageRatePercent()).isEqualTo(new BigDecimal("30.00"));

        JsonNode usageByWeekday = objectMapper.readTree(row.usageByWeekdayJson());
        assertThat(usageByWeekday.get("monday").decimalValue()).isEqualByComparingTo("33.33");
        assertThat(usageByWeekday.get("tuesday").decimalValue()).isEqualByComparingTo("66.67");
        assertThat(usageByWeekday.get("wednesday").decimalValue()).isEqualByComparingTo("0.00");

        JsonNode peakUsage = objectMapper.readTree(row.peakUsageJson());
        assertThat(peakUsage.get("startHour").intValue()).isEqualTo(20);
        assertThat(peakUsage.get("endHour").intValue()).isEqualTo(21);
        assertThat(peakUsage.get("mostUsedWeekday").textValue()).isEqualTo("tuesday");

        JsonNode missionSummary = objectMapper.readTree(row.missionSummaryJson());
        assertThat(missionSummary.get("totalMissionCount").intValue()).isEqualTo(3);
        assertThat(missionSummary.get("completedMissionCount").intValue()).isEqualTo(2);
        assertThat(missionSummary.get("rejectedRequestCount").intValue()).isEqualTo(1);

        JsonNode appealSummary = objectMapper.readTree(row.appealSummaryJson());
        assertThat(appealSummary.get("totalAppeals").intValue()).isEqualTo(5);
        assertThat(appealSummary.get("approvedAppeals").intValue()).isEqualTo(3);
        assertThat(appealSummary.get("rejectedAppeals").intValue()).isEqualTo(1);

        JsonNode appealHighlights = objectMapper.readTree(row.appealHighlightsJson());
        assertThat(appealHighlights.path("topSuccessfulRequester").path("requesterId").isNull())
                .isTrue();
        assertThat(
                        appealHighlights
                                .path("topAcceptedApprover")
                                .path("recentAcceptedAppeals")
                                .isArray())
                .isTrue();

        assertThat(row.communicationScore()).isEqualByComparingTo(new BigDecimal("74.83"));
    }

    @Test
    @DisplayName("process - total_quota_bytes는 주간 합산이 아닌 단일 snapshot 값을 사용한다")
    void process_usesSingleQuotaSnapshot() {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "monthly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-monthly-recap-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);

        List<MonthlyWeeklyRecapSnapshot> snapshots =
                List.of(
                        new MonthlyWeeklyRecapSnapshot(
                                LocalDate.of(2026, 3, 2), 1000L, 3000L, "{}", "{}", 0, 0, 0, 0),
                        new MonthlyWeeklyRecapSnapshot(
                                LocalDate.of(2026, 3, 9), 1000L, 7000L, "{}", "{}", 0, 0, 0, 0));

        when(aggregationRepository.aggregate(20L, targetMonth))
                .thenReturn(new MonthlyFamilyRecapSourceMetrics(snapshots, 9000L, 0, 0, 0));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(20L);

        assertThat(row.totalQuotaBytes()).isEqualTo(9000L);
    }

    @Test
    @DisplayName("process - 이의제기가 없고 미션만 있으면 소통점수는 미션 완료율 fallback을 사용한다")
    void process_withoutAppeals_withMissions_usesFallbackCommunicationScore() {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "monthly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-monthly-recap-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);

        List<MonthlyWeeklyRecapSnapshot> snapshots =
                List.of(
                        new MonthlyWeeklyRecapSnapshot(
                                LocalDate.of(2026, 3, 2), 500L, 3000L, "{}", "{}", 3, 1, 0, 0));

        when(aggregationRepository.aggregate(30L, targetMonth))
                .thenReturn(new MonthlyFamilyRecapSourceMetrics(snapshots, 3000L, 0, 0, 0));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(30L);

        assertThat(row.communicationScore()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    @DisplayName("process - 이의제기와 미션이 모두 0건이면 소통점수는 null이다")
    void process_withoutAppealsAndMissions_returnsNullCommunicationScore() {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "monthly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-monthly-recap-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(aggregationRepository.aggregate(40L, targetMonth))
                .thenReturn(new MonthlyFamilyRecapSourceMetrics(List.of(), 0L, 0, 0, 0));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(40L);

        assertThat(row.communicationScore()).isNull();
    }

    @Test
    @DisplayName("process - 집계값이 음수면 예외가 발생한다")
    void process_withNegativeAggregate_throwsException() {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "monthly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("targetMonth", "2026-03-01")
                                .toJobParameters());
        StepExecution stepExecution = new StepExecution("process-monthly-recap-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(aggregationRepository.aggregate(50L, targetMonth))
                .thenReturn(
                        new MonthlyFamilyRecapSourceMetrics(
                                List.of(
                                        new MonthlyWeeklyRecapSnapshot(
                                                LocalDate.of(2026, 3, 2),
                                                -1L,
                                                1000L,
                                                "{}",
                                                "{}",
                                                0,
                                                0,
                                                0,
                                                0)),
                                1000L,
                                0,
                                0,
                                0));

        processor.beforeStep(stepExecution);

        assertThatThrownBy(() -> processor.process(50L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be negative");
    }
}
