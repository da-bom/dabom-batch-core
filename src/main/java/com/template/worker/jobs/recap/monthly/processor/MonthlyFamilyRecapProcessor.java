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
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapRow;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyPeakUsage;
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

        List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots = sourceMetrics.fullWeekSnapshots();

        // 이번 이슈 범위에서는 full week 합산값만 월간 사용량에 반영
        long totalUsedBytes =
                fullWeekSnapshots.stream()
                        .mapToLong(MonthlyWeeklyRecapSnapshot::totalUsedBytes)
                        .sum();
        long totalQuotaBytes = sourceMetrics.totalQuotaBytes();
        BigDecimal usageRatePercent = calculatePercent(totalUsedBytes, totalQuotaBytes);

        // 주간 퍼센트 평균이 아니라 주간 사용량 가중합으로 월간 요일 비율 계산
        Map<String, BigDecimal> usageByWeekday =
                aggregateUsageByWeekday(familyId, fullWeekSnapshots);
        String usageByWeekdayJson = toJson(familyId, usageByWeekday, "usageByWeekday");

        String mostUsedWeekday = resolveMostUsedWeekday(usageByWeekday);
        MonthlyPeakUsage peakUsage =
                aggregatePeakUsage(familyId, fullWeekSnapshots, mostUsedWeekday);
        String peakUsageJson = toJson(familyId, peakUsage, "peakUsage");

        int totalMissionCount =
                fullWeekSnapshots.stream()
                        .mapToInt(MonthlyWeeklyRecapSnapshot::missionCreatedCount)
                        .sum();
        int completedMissionCount =
                fullWeekSnapshots.stream()
                        .mapToInt(MonthlyWeeklyRecapSnapshot::missionCompletedCount)
                        .sum();
        int rejectedRequestCount =
                fullWeekSnapshots.stream()
                        .mapToInt(MonthlyWeeklyRecapSnapshot::missionRejectedCount)
                        .sum();

        String missionSummaryJson =
                buildMissionSummaryJson(
                        familyId, totalMissionCount, completedMissionCount, rejectedRequestCount);

        // full week snapshot에서 합산한 이의제기 요약을 월간 JSON으로 변환
        String appealSummaryJson =
                buildAppealSummaryJson(
                        familyId,
                        sourceMetrics.totalAppeals(),
                        sourceMetrics.approvedAppeals(),
                        sourceMetrics.rejectedAppeals());

        String appealHighlightsJson = buildDefaultAppealHighlightsJson(familyId);

        BigDecimal communicationScore =
                calculateCommunicationScore(
                        sourceMetrics.approvedAppeals(),
                        sourceMetrics.rejectedAppeals(),
                        sourceMetrics.totalAppeals(),
                        totalMissionCount,
                        completedMissionCount);

        // 업서트 모델로 변환
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
        if (sourceMetrics.totalAppeals() < 0
                || sourceMetrics.approvedAppeals() < 0
                || sourceMetrics.rejectedAppeals() < 0) {
            throw new IllegalStateException(
                    "Appeal aggregate cannot be negative. familyId=" + familyId);
        }

        for (MonthlyWeeklyRecapSnapshot snapshot : sourceMetrics.fullWeekSnapshots()) {
            if (snapshot.totalUsedBytes() < 0 || snapshot.totalQuotaBytes() < 0) {
                throw new IllegalStateException(
                        "Weekly usage aggregate cannot be negative. familyId=" + familyId);
            }
            if (snapshot.missionCreatedCount() < 0
                    || snapshot.missionCompletedCount() < 0
                    || snapshot.missionRejectedCount() < 0
                    || snapshot.totalAppealCount() < 0
                    || snapshot.approvedAppealCount() < 0
                    || snapshot.rejectedAppealCount() < 0) {
                throw new IllegalStateException(
                        "Weekly count aggregate cannot be negative. familyId=" + familyId);
            }
        }
    }

    private Map<String, BigDecimal> aggregateUsageByWeekday(
            Long familyId, List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots) {
        Map<String, BigDecimal> weekdayUsedBytes = initWeekdayDecimalMap();

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

        long totalUsedBytes =
                fullWeekSnapshots.stream()
                        .mapToLong(MonthlyWeeklyRecapSnapshot::totalUsedBytes)
                        .sum();

        // 누적 바이트를 월 totalUsedBytes 대비 퍼센트로 재계산
        Map<String, BigDecimal> monthlyUsageByWeekday = new LinkedHashMap<>();
        for (String weekday : WEEKDAY_KEYS) {
            BigDecimal weekdayUsed = weekdayUsedBytes.getOrDefault(weekday, BigDecimal.ZERO);
            monthlyUsageByWeekday.put(weekday, calculatePercent(weekdayUsed, totalUsedBytes));
        }

        return monthlyUsageByWeekday;
    }

    private MonthlyPeakUsage aggregatePeakUsage(
            Long familyId,
            List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots,
            String mostUsedWeekday) {
        // peakBytes 우선, 동률이면 더 이른 시간대를 우선 선택
        WeeklyPeakUsageCandidate selected = new WeeklyPeakUsageCandidate(0, 1, 0L);

        for (MonthlyWeeklyRecapSnapshot snapshot : fullWeekSnapshots) {
            WeeklyPeakUsageCandidate candidate =
                    parsePeakUsageJson(
                            familyId, snapshot.weekStartDate(), snapshot.peakUsageJson());

            if (candidate.isBetterThan(selected)) {
                selected = candidate;
            }
        }

        return new MonthlyPeakUsage(selected.startHour(), selected.endHour(), mostUsedWeekday);
    }

    private String buildMissionSummaryJson(
            Long familyId,
            int totalMissionCount,
            int completedMissionCount,
            int rejectedRequestCount) {
        Map<String, Integer> missionSummary = new LinkedHashMap<>();
        missionSummary.put("totalMissionCount", totalMissionCount);
        missionSummary.put("completedMissionCount", completedMissionCount);
        missionSummary.put("rejectedRequestCount", rejectedRequestCount);

        return toJson(familyId, missionSummary, "missionSummary");
    }

    private String buildAppealSummaryJson(
            Long familyId, int totalAppeals, int approvedAppeals, int rejectedAppeals) {
        Map<String, Integer> appealSummary = new LinkedHashMap<>();
        appealSummary.put("totalAppeals", totalAppeals);
        appealSummary.put("approvedAppeals", approvedAppeals);
        appealSummary.put("rejectedAppeals", rejectedAppeals);

        return toJson(familyId, appealSummary, "appealSummary");
    }

    private String buildDefaultAppealHighlightsJson(Long familyId) {
        Map<String, Object> topSuccessfulRequester = new LinkedHashMap<>();
        topSuccessfulRequester.put("requesterId", null);
        topSuccessfulRequester.put("requesterName", null);
        topSuccessfulRequester.put("approvedAppealCount", 0);
        topSuccessfulRequester.put("recentApprovedAppeals", List.of());

        Map<String, Object> topAcceptedApprover = new LinkedHashMap<>();
        topAcceptedApprover.put("approverId", null);
        topAcceptedApprover.put("approverName", null);
        topAcceptedApprover.put("approvedAppealCount", 0);
        topAcceptedApprover.put("recentAcceptedAppeals", List.of());

        Map<String, Object> highlights = new LinkedHashMap<>();
        highlights.put("topSuccessfulRequester", topSuccessfulRequester);
        highlights.put("topAcceptedApprover", topAcceptedApprover);

        return toJson(familyId, highlights, "appealHighlights");
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
                JsonNode valueNode = root.get(weekday);
                usageByWeekday.put(weekday, parseBigDecimal(valueNode));
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

    private WeeklyPeakUsageCandidate parsePeakUsageJson(
            Long familyId, LocalDate weekStartDate, String peakUsageJson) {
        if (peakUsageJson == null || peakUsageJson.isBlank()) {
            return new WeeklyPeakUsageCandidate(0, 1, 0L);
        }

        try {
            JsonNode root = objectMapper.readTree(peakUsageJson);
            int startHour = root.path("startHour").asInt(0);
            int endHour = root.path("endHour").asInt(startHour + 1);
            long peakBytes = root.path("peakBytes").asLong(0L);
            return new WeeklyPeakUsageCandidate(startHour, endHour, peakBytes);
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
            int completedMissionCount) {
        if (totalAppeals > 0) {
            // NORMAL 이의제기가 1건 이상이면 가이드 수식(A/B/C)으로 계산
            int respondedAppeals = approvedAppeals + rejectedAppeals;

            BigDecimal factorA =
                    respondedAppeals == 0
                            ? BigDecimal.ZERO
                            : divide(approvedAppeals, respondedAppeals);
            BigDecimal factorB = divide(respondedAppeals, totalAppeals);
            BigDecimal factorC =
                    approvedAppeals == 0
                            ? BigDecimal.ZERO
                            : divide(completedMissionCount, approvedAppeals).min(BigDecimal.ONE);

            return factorA.multiply(BigDecimal.valueOf(0.5))
                    .add(factorB.multiply(BigDecimal.valueOf(0.3)))
                    .add(factorC.multiply(BigDecimal.valueOf(0.2)))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        if (totalMissionCount > 0) {
            // 이의제기 0건이면 미션 완료율 fallback 점수를 사용
            return calculatePercent(completedMissionCount, totalMissionCount);
        }

        // 이의제기/미션 모두 없으면 점수 미산정(null)
        return null;
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

    private record WeeklyPeakUsageCandidate(int startHour, int endHour, long peakBytes) {

        private boolean isBetterThan(WeeklyPeakUsageCandidate current) {
            if (peakBytes > current.peakBytes) {
                return true;
            }
            if (peakBytes < current.peakBytes) {
                return false;
            }
            if (startHour < current.startHour) {
                return true;
            }
            if (startHour > current.startHour) {
                return false;
            }
            return endHour < current.endHour;
        }
    }
}
