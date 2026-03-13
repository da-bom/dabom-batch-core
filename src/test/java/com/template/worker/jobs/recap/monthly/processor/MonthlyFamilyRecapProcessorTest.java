package com.template.worker.jobs.recap.monthly.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.worker.jobs.recap.monthly.model.MonthlyAppealHighlights;
import com.template.worker.jobs.recap.monthly.model.MonthlyAppealSummary;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapRow;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyMissionSummary;
import com.template.worker.jobs.recap.monthly.model.MonthlyUsagePeakCandidate;
import com.template.worker.jobs.recap.monthly.model.MonthlyUsageSupplementMetrics;
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
    @DisplayName("process - full week와 partial week를 합쳐 월간 리캡 row를 만든다")
    void process_buildsMonthlyRecapRowWithPartialSupplement() throws Exception {
        StepExecution stepExecution = createStepExecution("2026-03-01");
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
                                1,
                                1,
                                0),
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
                                2,
                                1,
                                1));

        MonthlyUsageSupplementMetrics partialUsageMetrics =
                new MonthlyUsageSupplementMetrics(
                        500L,
                        weekdayBytes(0L, 0L, 500L, 0L, 0L, 0L, 0L),
                        new MonthlyUsagePeakCandidate(22, 23, 900L));

        MonthlyAppealHighlights appealHighlights =
                new MonthlyAppealHighlights(
                        new MonthlyAppealHighlights.TopSuccessfulRequester(
                                101L,
                                "김민지",
                                3,
                                List.of(
                                        new MonthlyAppealHighlights.RecentApprovedAppeal(
                                                91L,
                                                201L,
                                                "김철수",
                                                "야간 차단 해제를 요청했어요.",
                                                "2026-03-21T14:32:00"))),
                        new MonthlyAppealHighlights.TopAcceptedApprover(
                                201L,
                                "김철수",
                                3,
                                List.of(
                                        new MonthlyAppealHighlights.RecentAcceptedAppeal(
                                                91L,
                                                101L,
                                                "김민지",
                                                "야간 차단 해제를 요청했어요.",
                                                "2026-03-21T14:32:00"))));

        when(aggregationRepository.aggregate(10L, targetMonth))
                .thenReturn(
                        new MonthlyFamilyRecapSourceMetrics(
                                snapshots,
                                10000L,
                                partialUsageMetrics,
                                new MonthlyMissionSummary(4, 3, 2),
                                new MonthlyAppealSummary(5, 3, 1),
                                0,
                                0,
                                appealHighlights));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(10L);

        assertThat(row.familyId()).isEqualTo(10L);
        assertThat(row.reportMonth()).isEqualTo(targetMonth);
        assertThat(row.totalUsedBytes()).isEqualTo(3500L);
        assertThat(row.totalQuotaBytes()).isEqualTo(10000L);
        assertThat(row.usageRatePercent()).isEqualByComparingTo("35.00");

        JsonNode usageByWeekday = objectMapper.readTree(row.usageByWeekdayJson());
        assertThat(usageByWeekday.get("monday").decimalValue()).isEqualByComparingTo("28.57");
        assertThat(usageByWeekday.get("tuesday").decimalValue()).isEqualByComparingTo("57.14");
        assertThat(usageByWeekday.get("wednesday").decimalValue()).isEqualByComparingTo("14.29");

        JsonNode peakUsage = objectMapper.readTree(row.peakUsageJson());
        assertThat(peakUsage.get("startHour").intValue()).isEqualTo(22);
        assertThat(peakUsage.get("endHour").intValue()).isEqualTo(23);
        assertThat(peakUsage.get("mostUsedWeekday").textValue()).isEqualTo("tuesday");

        JsonNode missionSummary = objectMapper.readTree(row.missionSummaryJson());
        assertThat(missionSummary.get("totalMissionCount").intValue()).isEqualTo(4);
        assertThat(missionSummary.get("completedMissionCount").intValue()).isEqualTo(3);
        assertThat(missionSummary.get("rejectedRequestCount").intValue()).isEqualTo(2);

        JsonNode appealSummary = objectMapper.readTree(row.appealSummaryJson());
        assertThat(appealSummary.get("totalAppeals").intValue()).isEqualTo(5);
        assertThat(appealSummary.get("approvedAppeals").intValue()).isEqualTo(3);
        assertThat(appealSummary.get("rejectedAppeals").intValue()).isEqualTo(1);

        JsonNode highlights = objectMapper.readTree(row.appealHighlightsJson());
        assertThat(highlights.path("topSuccessfulRequester").path("requesterId").longValue())
                .isEqualTo(101L);
        assertThat(
                        highlights
                                .path("topSuccessfulRequester")
                                .path("recentApprovedAppeals")
                                .get(0)
                                .path("requestedAt")
                                .textValue())
                .isEqualTo("2026-03-21T14:32:00");
        assertThat(highlights.path("topAcceptedApprover").path("approverId").longValue())
                .isEqualTo(201L);

        assertThat(row.communicationScore()).isEqualByComparingTo(new BigDecimal("77.75"));
    }

    @Test
    @DisplayName("process - mostUsedWeekday tie는 monday부터 우선한다")
    void process_whenWeekdayUsageIsTied_prefersEarlierWeekday() throws Exception {
        StepExecution stepExecution = createStepExecution("2026-03-01");
        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);

        List<MonthlyWeeklyRecapSnapshot> snapshots =
                List.of(
                        new MonthlyWeeklyRecapSnapshot(
                                LocalDate.of(2026, 3, 2),
                                1000L,
                                3000L,
                                "{\"monday\":50.00,\"tuesday\":50.00,\"wednesday\":0.00,"
                                        + "\"thursday\":0.00,\"friday\":0.00,\"saturday\":0.00,"
                                        + "\"sunday\":0.00}",
                                "{\"startHour\":20,\"endHour\":21,\"peakBytes\":100}",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0));

        when(aggregationRepository.aggregate(20L, targetMonth))
                .thenReturn(
                        new MonthlyFamilyRecapSourceMetrics(
                                snapshots,
                                3000L,
                                MonthlyUsageSupplementMetrics.empty(),
                                MonthlyMissionSummary.empty(),
                                MonthlyAppealSummary.empty(),
                                0,
                                0,
                                MonthlyAppealHighlights.empty()));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(20L);

        JsonNode peakUsage = objectMapper.readTree(row.peakUsageJson());
        assertThat(peakUsage.get("mostUsedWeekday").textValue()).isEqualTo("monday");
    }

    @Test
    @DisplayName("process - 이의제기가 없고 미션만 있으면 소통점수는 미션 완료율 fallback을 사용한다")
    void process_withoutAppeals_withMissions_usesFallbackCommunicationScore() {
        StepExecution stepExecution = createStepExecution("2026-03-01");
        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(aggregationRepository.aggregate(30L, targetMonth))
                .thenReturn(
                        new MonthlyFamilyRecapSourceMetrics(
                                List.of(),
                                3000L,
                                MonthlyUsageSupplementMetrics.empty(),
                                new MonthlyMissionSummary(3, 1, 0),
                                MonthlyAppealSummary.empty(),
                                0,
                                0,
                                MonthlyAppealHighlights.empty()));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(30L);

        assertThat(row.communicationScore()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    @DisplayName("process - 신규 요청이 없어도 carry in이 있으면 소통점수를 계산한다")
    void process_withCarryInOnly_returnsCarryInBasedCommunicationScore() {
        StepExecution stepExecution = createStepExecution("2026-03-01");
        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(aggregationRepository.aggregate(35L, targetMonth))
                .thenReturn(
                        new MonthlyFamilyRecapSourceMetrics(
                                List.of(),
                                3000L,
                                MonthlyUsageSupplementMetrics.empty(),
                                MonthlyMissionSummary.empty(),
                                new MonthlyAppealSummary(0, 1, 0),
                                0,
                                2,
                                MonthlyAppealHighlights.empty()));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(35L);

        assertThat(row.communicationScore()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("process - 이의제기와 미션이 모두 0건이면 소통점수는 null이다")
    void process_withoutAppealsAndMissions_returnsNullCommunicationScore() {
        StepExecution stepExecution = createStepExecution("2026-03-01");
        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(aggregationRepository.aggregate(40L, targetMonth))
                .thenReturn(
                        new MonthlyFamilyRecapSourceMetrics(
                                List.of(),
                                0L,
                                MonthlyUsageSupplementMetrics.empty(),
                                MonthlyMissionSummary.empty(),
                                MonthlyAppealSummary.empty(),
                                0,
                                0,
                                MonthlyAppealHighlights.empty()));

        processor.beforeStep(stepExecution);
        MonthlyFamilyRecapRow row = processor.process(40L);

        assertThat(row.communicationScore()).isNull();
    }

    @Test
    @DisplayName("process - 집계값이 음수면 예외가 발생한다")
    void process_withNegativeAggregate_throwsException() {
        StepExecution stepExecution = createStepExecution("2026-03-01");
        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(aggregationRepository.aggregate(50L, targetMonth))
                .thenReturn(
                        new MonthlyFamilyRecapSourceMetrics(
                                List.of(),
                                1000L,
                                new MonthlyUsageSupplementMetrics(
                                        -1L,
                                        weekdayBytes(0L, 0L, 0L, 0L, 0L, 0L, 0L),
                                        MonthlyUsagePeakCandidate.empty()),
                                MonthlyMissionSummary.empty(),
                                MonthlyAppealSummary.empty(),
                                0,
                                0,
                                MonthlyAppealHighlights.empty()));

        processor.beforeStep(stepExecution);

        assertThatThrownBy(() -> processor.process(50L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be negative");
    }

    private StepExecution createStepExecution(String targetMonth) {
        JobExecution jobExecution =
                new JobExecution(
                        new JobInstance(1L, "monthly-family-recap-job"),
                        new JobParametersBuilder()
                                .addString("targetMonth", targetMonth)
                                .toJobParameters());
        return new StepExecution("process-monthly-recap-step", jobExecution);
    }

    private Map<String, Long> weekdayBytes(
            long monday,
            long tuesday,
            long wednesday,
            long thursday,
            long friday,
            long saturday,
            long sunday) {
        Map<String, Long> bytesByWeekday = new LinkedHashMap<>();
        bytesByWeekday.put("monday", monday);
        bytesByWeekday.put("tuesday", tuesday);
        bytesByWeekday.put("wednesday", wednesday);
        bytesByWeekday.put("thursday", thursday);
        bytesByWeekday.put("friday", friday);
        bytesByWeekday.put("saturday", saturday);
        bytesByWeekday.put("sunday", sunday);
        return bytesByWeekday;
    }
}
