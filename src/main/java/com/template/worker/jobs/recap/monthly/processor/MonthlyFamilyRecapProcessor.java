package com.template.worker.jobs.recap.monthly.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.worker.jobs.recap.monthly.model.MonthlyAppealHighlights;
import com.template.worker.jobs.recap.monthly.model.MonthlyAppealSummary;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapRow;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyMissionSummary;
import com.template.worker.jobs.recap.monthly.model.MonthlyPeakUsage;
import com.template.worker.jobs.recap.monthly.model.MonthlyUsagePeakCandidate;
import com.template.worker.jobs.recap.monthly.model.MonthlyUsageSupplementMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyWeeklyRecapSnapshot;
import com.template.worker.jobs.recap.monthly.query.MonthlyFamilyRecapAggregationRepository;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapJobParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapProcessor
        implements ItemProcessor<Long, MonthlyFamilyRecapRow>, StepExecutionListener {

    private static final List<String> WEEKDAY_KEYS =
            List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private final MonthlyFamilyRecapAggregationRepository aggregationRepository;
    private final MonthlyFamilyRecapJobParameterSupport parameterSupport;
    private final ObjectMapper objectMapper;

    private LocalDate targetMonth;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // 스텝 시작 시 월간 기준일을 고정
        targetMonth = parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
    }

    @Override
    public MonthlyFamilyRecapRow process(Long familyId) {
        // 가족별 원본 집계값 조회
        MonthlyFamilyRecapSourceMetrics sourceMetrics =
                aggregationRepository.aggregate(familyId, targetMonth);

        // writer 진입 전에 집계값 검증
        validateSourceMetrics(familyId, sourceMetrics);

        // 월 내부 full week snapshot
        List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots = sourceMetrics.fullWeekSnapshots();
        // 월 경계 partial raw 보강값
        MonthlyUsageSupplementMetrics partialUsageMetrics =
                normalizePartialUsageMetrics(sourceMetrics);
        MonthlyMissionSummary missionSummary = normalizeMissionSummary(sourceMetrics);
        MonthlyAppealSummary appealSummary = normalizeAppealSummary(sourceMetrics);
        MonthlyAppealHighlights appealHighlights = normalizeAppealHighlights(sourceMetrics);

        // 월 사용량은 full week snapshot 합계에 partial raw usage를 더해 만듦
        long totalUsedBytes =
                fullWeekSnapshots.stream()
                                .mapToLong(MonthlyWeeklyRecapSnapshot::totalUsedBytes)
                                .sum()
                        + partialUsageMetrics.totalUsedBytes();
        long totalQuotaBytes = sourceMetrics.totalQuotaBytes();
        BigDecimal usageRatePercent = calculatePercent(totalUsedBytes, totalQuotaBytes);

        // 주간 퍼센트 평균이 아니라 주간 사용량 가중합으로 월간 요일 비율 계산
        Map<String, BigDecimal> usageByWeekday =
                aggregateUsageByWeekday(
                        familyId, fullWeekSnapshots, partialUsageMetrics, totalUsedBytes);
        String usageByWeekdayJson = toJson(familyId, usageByWeekday, "usageByWeekday");

        // weekday tie 결과 사용
        String mostUsedWeekday = resolveMostUsedWeekday(usageByWeekday);
        MonthlyPeakUsage peakUsage =
                aggregatePeakUsage(
                        familyId, fullWeekSnapshots, partialUsageMetrics, mostUsedWeekday);
        String peakUsageJson = toJson(familyId, peakUsage, "peakUsage");

        // summary 직렬화
        String missionSummaryJson = toJson(familyId, missionSummary, "missionSummary");
        String appealSummaryJson = toJson(familyId, appealSummary, "appealSummary");
        String appealHighlightsJson = toJson(familyId, appealHighlights, "appealHighlights");

        // backlog 포함 score 계산
        BigDecimal communicationScore =
                calculateCommunicationScore(
                        appealSummary.approvedAppeals(),
                        appealSummary.rejectedAppeals(),
                        appealSummary.totalAppeals(),
                        missionSummary.totalMissionCount(),
                        missionSummary.completedMissionCount(),
                        sourceMetrics.appealCarryInCount(),
                        sourceMetrics.missionCarryInCount());

        return new MonthlyFamilyRecapRow(
                familyId,
                targetMonth,
                totalUsedBytes,
                totalQuotaBytes,
                usageRatePercent,
                usageByWeekdayJson,
                peakUsageJson,
                missionSummaryJson,
                appealSummaryJson,
                appealHighlightsJson,
                communicationScore);
    }

    private void validateSourceMetrics(
            Long familyId, MonthlyFamilyRecapSourceMetrics sourceMetrics) {
        if (sourceMetrics.totalQuotaBytes() < 0) {
            throw new IllegalStateException(
                    "Quota snapshot cannot be negative. familyId=" + familyId);
        }
        if (sourceMetrics.missionCarryInCount() < 0 || sourceMetrics.appealCarryInCount() < 0) {
            throw new IllegalStateException(
                    "Carry-in aggregate cannot be negative. familyId=" + familyId);
        }

        MonthlyUsageSupplementMetrics partialUsageMetrics =
                normalizePartialUsageMetrics(sourceMetrics);
        validatePartialUsageMetrics(familyId, partialUsageMetrics);

        MonthlyMissionSummary missionSummary = normalizeMissionSummary(sourceMetrics);
        validateMissionSummary(familyId, missionSummary);

        MonthlyAppealSummary appealSummary = normalizeAppealSummary(sourceMetrics);
        validateAppealSummary(familyId, appealSummary);

        MonthlyAppealHighlights appealHighlights = normalizeAppealHighlights(sourceMetrics);
        validateAppealHighlights(familyId, appealHighlights);

        for (MonthlyWeeklyRecapSnapshot snapshot : sourceMetrics.fullWeekSnapshots()) {
            validateWeeklySnapshot(familyId, snapshot);
        }
    }

    private void validatePartialUsageMetrics(
            Long familyId, MonthlyUsageSupplementMetrics partialUsageMetrics) {
        if (partialUsageMetrics.totalUsedBytes() < 0
                || partialUsageMetrics.peakUsageCandidate().peakBytes() < 0) {
            throw new IllegalStateException(
                    "Partial usage aggregate cannot be negative. familyId=" + familyId);
        }

        for (Long bytes : partialUsageMetrics.usageBytesByWeekday().values()) {
            if (bytes != null && bytes < 0) {
                throw new IllegalStateException(
                        "Partial weekday usage cannot be negative. familyId=" + familyId);
            }
        }
    }

    private void validateMissionSummary(Long familyId, MonthlyMissionSummary missionSummary) {
        if (missionSummary.totalMissionCount() < 0
                || missionSummary.completedMissionCount() < 0
                || missionSummary.rejectedRequestCount() < 0) {
            throw new IllegalStateException(
                    "Mission aggregate cannot be negative. familyId=" + familyId);
        }
    }

    private void validateAppealSummary(Long familyId, MonthlyAppealSummary appealSummary) {
        if (appealSummary.totalAppeals() < 0
                || appealSummary.approvedAppeals() < 0
                || appealSummary.rejectedAppeals() < 0) {
            throw new IllegalStateException(
                    "Appeal aggregate cannot be negative. familyId=" + familyId);
        }
    }

    private void validateAppealHighlights(Long familyId, MonthlyAppealHighlights appealHighlights) {
        if (appealHighlights.topSuccessfulRequester().approvedAppealCount() < 0
                || appealHighlights.topAcceptedApprover().approvedAppealCount() < 0) {
            throw new IllegalStateException(
                    "Appeal highlight aggregate cannot be negative. familyId=" + familyId);
        }
    }

    private void validateWeeklySnapshot(Long familyId, MonthlyWeeklyRecapSnapshot snapshot) {
        if (snapshot.totalUsedBytes() < 0 || snapshot.totalQuotaBytes() < 0) {
            throw new IllegalStateException(
                    "Weekly usage aggregate cannot be negative. familyId=" + familyId);
        }
        if (snapshot.missionCreatedCount() < 0
                || snapshot.missionCompletedCount() < 0
                || snapshot.missionRejectedCount() < 0) {
            throw new IllegalStateException(
                    "Weekly mission aggregate cannot be negative. familyId=" + familyId);
        }
        if (snapshot.totalAppealCount() < 0
                || snapshot.approvedAppealCount() < 0
                || snapshot.rejectedAppealCount() < 0) {
            throw new IllegalStateException(
                    "Weekly appeal aggregate cannot be negative. familyId=" + familyId);
        }
    }

    private MonthlyUsageSupplementMetrics normalizePartialUsageMetrics(
            MonthlyFamilyRecapSourceMetrics sourceMetrics) {
        // partial raw 보강이 없으면 빈 값 사용
        return sourceMetrics.partialUsageMetrics() == null
                ? MonthlyUsageSupplementMetrics.empty()
                : sourceMetrics.partialUsageMetrics();
    }

    private MonthlyMissionSummary normalizeMissionSummary(
            MonthlyFamilyRecapSourceMetrics sourceMetrics) {
        // summary null 방어
        return sourceMetrics.missionSummary() == null
                ? MonthlyMissionSummary.empty()
                : sourceMetrics.missionSummary();
    }

    private MonthlyAppealSummary normalizeAppealSummary(
            MonthlyFamilyRecapSourceMetrics sourceMetrics) {
        // summary null 방어
        return sourceMetrics.appealSummary() == null
                ? MonthlyAppealSummary.empty()
                : sourceMetrics.appealSummary();
    }

    private MonthlyAppealHighlights normalizeAppealHighlights(
            MonthlyFamilyRecapSourceMetrics sourceMetrics) {
        // highlight null 방어
        return sourceMetrics.appealHighlights() == null
                ? MonthlyAppealHighlights.empty()
                : sourceMetrics.appealHighlights();
    }

    private Map<String, BigDecimal> aggregateUsageByWeekday(
            Long familyId,
            List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots,
            MonthlyUsageSupplementMetrics partialUsageMetrics,
            long totalUsedBytes) {
        Map<String, BigDecimal> weekdayUsedBytes = initWeekdayDecimalMap();

        // weekly 비율을 바이트로 환산
        for (MonthlyWeeklyRecapSnapshot snapshot : fullWeekSnapshots) {
            Map<String, BigDecimal> weeklyUsageByWeekday =
                    parseUsageByWeekdayJson(
                            familyId, snapshot.weekStartDate(), snapshot.usageByWeekdayJson());

            for (String weekday : WEEKDAY_KEYS) {
                BigDecimal usagePercent =
                        weeklyUsageByWeekday.getOrDefault(weekday, BigDecimal.ZERO);
                // 주간 퍼센트를 주간 totalUsedBytes에 환산해 월간 누적 바이트로 변환
                BigDecimal weightedBytes =
                        BigDecimal.valueOf(snapshot.totalUsedBytes())
                                .multiply(usagePercent)
                                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                weekdayUsedBytes.put(weekday, weekdayUsedBytes.get(weekday).add(weightedBytes));
            }
        }

        // partial week는 raw bytes를 그대로 더해 월 기준 바이트 총합을 만듦
        for (String weekday : WEEKDAY_KEYS) {
            weekdayUsedBytes.put(
                    weekday,
                    weekdayUsedBytes
                            .get(weekday)
                            .add(
                                    BigDecimal.valueOf(
                                            partialUsageMetrics
                                                    .usageBytesByWeekday()
                                                    .getOrDefault(weekday, 0L))));
        }

        // 월 퍼센트 재계산
        Map<String, BigDecimal> monthlyUsageByWeekday = new LinkedHashMap<>();
        for (String weekday : WEEKDAY_KEYS) {
            monthlyUsageByWeekday.put(
                    weekday,
                    calculatePercent(
                            weekdayUsedBytes.getOrDefault(weekday, BigDecimal.ZERO),
                            totalUsedBytes));
        }
        return monthlyUsageByWeekday;
    }

    private MonthlyPeakUsage aggregatePeakUsage(
            Long familyId,
            List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots,
            MonthlyUsageSupplementMetrics partialUsageMetrics,
            String mostUsedWeekday) {
        // partial raw peak를 기본 후보로 두고 full week peak들과 비교해 최종 시간대를 고름
        MonthlyUsagePeakCandidate selected = partialUsageMetrics.peakUsageCandidate();

        for (MonthlyWeeklyRecapSnapshot snapshot : fullWeekSnapshots) {
            MonthlyUsagePeakCandidate candidate =
                    parsePeakUsageJson(
                            familyId, snapshot.weekStartDate(), snapshot.peakUsageJson());
            if (candidate.isBetterThan(selected)) {
                selected = candidate;
            }
        }

        return new MonthlyPeakUsage(selected.startHour(), selected.endHour(), mostUsedWeekday);
    }

    private Map<String, BigDecimal> parseUsageByWeekdayJson(
            Long familyId, LocalDate weekStartDate, String usageByWeekdayJson) {
        Map<String, BigDecimal> usageByWeekday = initWeekdayDecimalMap();

        if (usageByWeekdayJson == null || usageByWeekdayJson.isBlank()) {
            return usageByWeekday;
        }

        try {
            JsonNode root = objectMapper.readTree(usageByWeekdayJson);
            for (String weekday : WEEKDAY_KEYS) {
                usageByWeekday.put(weekday, parseBigDecimal(root.get(weekday)));
            }
            return usageByWeekday;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse weekly usageByWeekday JSON. familyId="
                            + familyId
                            + ", weekStartDate="
                            + weekStartDate,
                    exception);
        }
    }

    private MonthlyUsagePeakCandidate parsePeakUsageJson(
            Long familyId, LocalDate weekStartDate, String peakUsageJson) {
        if (peakUsageJson == null || peakUsageJson.isBlank()) {
            return MonthlyUsagePeakCandidate.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(peakUsageJson);
            int startHour = root.path("startHour").asInt(0);
            int endHour = root.path("endHour").asInt(startHour + 1);
            long peakBytes = root.path("peakBytes").asLong(0L);
            return new MonthlyUsagePeakCandidate(startHour, endHour, peakBytes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse weekly peakUsage JSON. familyId="
                            + familyId
                            + ", weekStartDate="
                            + weekStartDate,
                    exception);
        }
    }

    private String resolveMostUsedWeekday(Map<String, BigDecimal> usageByWeekday) {
        String mostUsedWeekday = WEEKDAY_KEYS.get(0);
        BigDecimal mostUsedValue = usageByWeekday.getOrDefault(mostUsedWeekday, BigDecimal.ZERO);

        // tie는 앞선 요일 유지
        for (String weekday : WEEKDAY_KEYS) {
            BigDecimal current = usageByWeekday.getOrDefault(weekday, BigDecimal.ZERO);
            if (current.compareTo(mostUsedValue) > 0) {
                mostUsedWeekday = weekday;
                mostUsedValue = current;
            }
        }

        return mostUsedWeekday;
    }

    private BigDecimal calculateCommunicationScore(
            int approvedAppeals,
            int rejectedAppeals,
            int totalAppeals,
            int totalMissionCount,
            int completedMissionCount,
            int appealCarryInCount,
            int missionCarryInCount) {
        int respondedAppeals = approvedAppeals + rejectedAppeals;
        // 월초 backlog 포함 분모
        int appealBase = appealCarryInCount + totalAppeals;
        int missionBase = missionCarryInCount + totalMissionCount;

        // 일이 없던 축은 계산 제외
        BigDecimal appealResponseRate =
                appealBase > 0 ? divide(respondedAppeals, appealBase) : null;
        BigDecimal missionCompletionRate =
                missionBase > 0 ? divide(completedMissionCount, missionBase) : null;

        if (appealResponseRate == null && missionCompletionRate == null) {
            return null;
        }
        // 한 축만 있으면 단일 축 점수
        if (appealResponseRate == null) {
            return toPercent(missionCompletionRate);
        }
        if (missionCompletionRate == null) {
            return toPercent(appealResponseRate);
        }

        // 두 축 모두 있으면 가중 평균
        return appealResponseRate
                .multiply(BigDecimal.valueOf(0.55))
                .add(missionCompletionRate.multiply(BigDecimal.valueOf(0.45)))
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toPercent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> initWeekdayDecimalMap() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String weekday : WEEKDAY_KEYS) {
            map.put(weekday, BigDecimal.ZERO);
        }
        return map;
    }

    private BigDecimal parseBigDecimal(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(valueNode.asText("0"));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
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
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePercent(BigDecimal numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return numerator
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
