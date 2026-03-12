package com.template.worker.jobs.recap.weekly.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapRow;
import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.weekly.model.WeeklyPeakUsage;
import com.template.worker.jobs.recap.weekly.query.WeeklyFamilyRecapAggregationRepository;
import com.template.worker.jobs.recap.weekly.support.WeekStartDateParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapProcessor
        implements ItemProcessor<Long, WeeklyFamilyRecapRow>, StepExecutionListener {

    private static final List<String> WEEKDAY_KEYS =
            List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private final WeeklyFamilyRecapAggregationRepository aggregationRepository;
    private final WeekStartDateParameterSupport parameterSupport;
    private final ObjectMapper objectMapper;

    private LocalDate weekStartDate;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // 스텝 시작 시 주간 기준일을 고정
        weekStartDate = parameterSupport.resolveWeekStartDate(stepExecution.getJobParameters());
    }

    @Override
    public WeeklyFamilyRecapRow process(Long familyId) {
        // 가족별 원본 집계값을 조회
        WeeklyFamilyRecapSourceMetrics sourceMetrics =
                aggregationRepository.aggregate(familyId, weekStartDate);

        // writer 진입 전에 집계값 검증
        validateSourceMetrics(familyId, sourceMetrics);

        BigDecimal usageRatePercent =
                calculatePercent(sourceMetrics.totalUsedBytes(), sourceMetrics.totalQuotaBytes());
        String usageByWeekdayJson =
                buildUsageByWeekdayJson(
                        familyId,
                        sourceMetrics.usageBytesByWeekday(),
                        sourceMetrics.totalUsedBytes());
        String peakUsageJson = buildPeakUsageJson(familyId, sourceMetrics.peakUsage());

        // 업서트 모델로 변환
        return new WeeklyFamilyRecapRow(
                familyId,
                weekStartDate,
                sourceMetrics.totalUsedBytes(),
                sourceMetrics.totalQuotaBytes(),
                usageRatePercent,
                usageByWeekdayJson,
                peakUsageJson,
                sourceMetrics.missionCreatedCount(),
                sourceMetrics.missionCompletedCount(),
                sourceMetrics.missionRejectedCount(),
                sourceMetrics.totalAppealCount(),
                sourceMetrics.approvedAppealCount(),
                sourceMetrics.rejectedAppealCount());
    }

    private void validateSourceMetrics(
            Long familyId, WeeklyFamilyRecapSourceMetrics sourceMetrics) {
        if (!DayOfWeek.MONDAY.equals(weekStartDate.getDayOfWeek())) {
            throw new IllegalStateException("weekStartDate must be Monday. familyId=" + familyId);
        }
        if (sourceMetrics.totalUsedBytes() < 0 || sourceMetrics.totalQuotaBytes() < 0) {
            throw new IllegalStateException(
                    "Usage aggregate cannot be negative. familyId=" + familyId);
        }
        if (sourceMetrics.missionCreatedCount() < 0
                || sourceMetrics.missionCompletedCount() < 0
                || sourceMetrics.missionRejectedCount() < 0
                || sourceMetrics.totalAppealCount() < 0
                || sourceMetrics.approvedAppealCount() < 0
                || sourceMetrics.rejectedAppealCount() < 0) {
            throw new IllegalStateException(
                    "Count aggregate cannot be negative. familyId=" + familyId);
        }
    }

    private String buildUsageByWeekdayJson(
            Long familyId, Map<String, Long> usageBytesByWeekday, long totalUsedBytes) {
        // 7요일 고정 순서로 퍼센트 맵을 만들고 JSON 직렬화
        Map<String, BigDecimal> usageByWeekday = new LinkedHashMap<>();
        for (String weekday : WEEKDAY_KEYS) {
            long weekdayUsedBytes = usageBytesByWeekday.getOrDefault(weekday, 0L);
            usageByWeekday.put(weekday, calculatePercent(weekdayUsedBytes, totalUsedBytes));
        }
        return toJson(familyId, usageByWeekday, "usageByWeekday");
    }

    private String buildPeakUsageJson(Long familyId, WeeklyPeakUsage peakUsage) {
        // 주간 피크 정보를 JSON으로 직렬화
        return toJson(familyId, peakUsage, "peakUsage");
    }

    private String toJson(Long familyId, Object target, String targetName) {
        try {
            return objectMapper.writeValueAsString(target);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize " + targetName + " to JSON. familyId=" + familyId,
                    exception);
        }
    }

    private BigDecimal calculatePercent(long numerator, long denominator) {
        // 분모가 0이면 0.00을 반환
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
