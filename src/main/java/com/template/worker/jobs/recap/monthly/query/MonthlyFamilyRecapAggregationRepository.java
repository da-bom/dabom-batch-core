package com.template.worker.jobs.recap.monthly.query;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.recap.monthly.model.MonthlyAppealHighlights;
import com.template.worker.jobs.recap.monthly.model.MonthlyAppealSummary;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyMissionSummary;
import com.template.worker.jobs.recap.monthly.model.MonthlyUsagePeakCandidate;
import com.template.worker.jobs.recap.monthly.model.MonthlyUsageSupplementMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyWeeklyRecapSnapshot;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapAggregationRepository {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String APPROVED_APPEAL_COUNT = "approved_appeal_count";

    private static final String READ_FULL_WEEKLY_RECAP_ROWS_SQL =
            """
            SELECT week_start_date,
                   total_used_bytes,
                   total_quota_bytes,
                   usage_by_weekday,
                   peak_usage,
                   mission_created_count,
                   mission_completed_count,
                   mission_rejected_count,
                   total_appeal_count,
                   approved_appeal_count,
                   rejected_appeal_count
            FROM family_recap_weekly
            WHERE family_id = :familyId
              AND week_start_date >= :monthStartDate
              AND week_start_date <= :lastFullWeekStartDate
            ORDER BY week_start_date ASC
            """;

    private static final String READ_LATEST_QUOTA_SNAPSHOT_FROM_WEEKLY_SQL =
            """
            SELECT total_quota_bytes
            FROM family_recap_weekly
            WHERE family_id = :familyId
              AND week_start_date < :monthEndExclusiveDate
              AND week_start_date >= :overlapStartDate
            ORDER BY week_start_date DESC
            LIMIT 1
            """;

    private static final String READ_FAMILY_QUOTA_BYTES_SQL =
            """
            SELECT total_quota_bytes
            FROM family
            WHERE id = :familyId
              AND deleted_at IS NULL
            """;

    private static final String READ_TOTAL_USED_BYTES_IN_RANGE_SQL =
            """
            SELECT COALESCE(SUM(bytes_used), 0)
            FROM usage_record
            WHERE family_id = :familyId
              AND event_time >= :rangeStart
              AND event_time < :rangeEndExclusive
              AND deleted_at IS NULL
            """;

    private static final String READ_USAGE_BY_WEEKDAY_IN_RANGE_SQL =
            """
            SELECT DAYOFWEEK(event_time) AS day_of_week,
                   COALESCE(SUM(bytes_used), 0) AS total_bytes
            FROM usage_record
            WHERE family_id = :familyId
              AND event_time >= :rangeStart
              AND event_time < :rangeEndExclusive
              AND deleted_at IS NULL
            GROUP BY DAYOFWEEK(event_time)
            """;

    private static final String READ_USAGE_BY_HOUR_IN_RANGE_SQL =
            """
            SELECT HOUR(event_time) AS start_hour,
                   COALESCE(SUM(bytes_used), 0) AS total_bytes
            FROM usage_record
            WHERE family_id = :familyId
              AND event_time >= :rangeStart
              AND event_time < :rangeEndExclusive
              AND deleted_at IS NULL
            GROUP BY HOUR(event_time)
            """;

    private static final String READ_MISSION_CREATED_COUNT_IN_RANGE_SQL =
            """
            SELECT COUNT(*)
            FROM mission_item
            WHERE family_id = :familyId
              AND created_at >= :rangeStart
              AND created_at < :rangeEndExclusive
              AND deleted_at IS NULL
            """;

    private static final String READ_MISSION_COMPLETED_COUNT_IN_RANGE_SQL =
            """
            SELECT COUNT(*)
            FROM mission_item
            WHERE family_id = :familyId
              AND status = 'COMPLETED'
              AND completed_at >= :rangeStart
              AND completed_at < :rangeEndExclusive
              AND deleted_at IS NULL
            """;

    private static final String READ_MISSION_REJECTED_COUNT_IN_RANGE_SQL =
            """
            SELECT COUNT(*)
            FROM mission_request mr
            JOIN mission_item mi ON mr.mission_item_id = mi.id
            WHERE mi.family_id = :familyId
              AND mr.status = 'REJECTED'
              AND mr.resolved_at >= :rangeStart
              AND mr.resolved_at < :rangeEndExclusive
              AND mr.deleted_at IS NULL
              AND mi.deleted_at IS NULL
            """;

    private static final String READ_MISSION_CARRY_IN_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM mission_item mi
            WHERE mi.family_id = :familyId
              AND mi.created_at < :monthStart
              AND mi.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM mission_log ml
                  WHERE ml.mission_item_id = mi.id
                    AND ml.action_type IN ('MISSION_COMPLETED', 'MISSION_CANCELLED')
                    AND ml.created_at < :monthStart
                    AND ml.deleted_at IS NULL
              )
            """;

    private static final String READ_TOTAL_APPEAL_COUNT_IN_RANGE_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.created_at >= :rangeStart
              AND pa.created_at < :rangeEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_APPROVED_APPEAL_COUNT_IN_RANGE_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_at >= :rangeStart
              AND pa.resolved_at < :rangeEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_REJECTED_APPEAL_COUNT_IN_RANGE_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'REJECTED'
              AND pa.resolved_at >= :rangeStart
              AND pa.resolved_at < :rangeEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_APPEAL_CARRY_IN_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.created_at < :monthStart
              AND (pa.resolved_at IS NULL OR pa.resolved_at >= :monthStart)
              AND (pa.cancelled_at IS NULL OR pa.cancelled_at >= :monthStart)
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_TOP_SUCCESSFUL_REQUESTER_SQL =
            """
            SELECT pa.requester_id AS requester_id,
                   requester.name AS requester_name,
                   COUNT(*) AS approved_appeal_count,
                   MAX(pa.resolved_at) AS latest_resolved_at
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            JOIN customer requester ON pa.requester_id = requester.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_at >= :monthStart
              AND pa.resolved_at < :monthEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
              AND requester.deleted_at IS NULL
            GROUP BY pa.requester_id, requester.name
            ORDER BY approved_appeal_count DESC, latest_resolved_at DESC, requester_id ASC
            LIMIT 1
            """;

    private static final String READ_RECENT_APPROVED_APPEALS_SQL =
            """
            SELECT pa.id AS appeal_id,
                   pa.resolved_by_id AS approver_id,
                   approver.name AS approver_name,
                   pa.request_reason,
                   pa.created_at AS requested_at
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            LEFT JOIN customer approver
              ON pa.resolved_by_id = approver.id
             AND approver.deleted_at IS NULL
            WHERE pas.family_id = :familyId
              AND pa.requester_id = :requesterId
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_at >= :monthStart
              AND pa.resolved_at < :monthEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            ORDER BY pa.resolved_at DESC, pa.id DESC
            LIMIT 3
            """;

    private static final String READ_TOP_ACCEPTED_APPROVER_SQL =
            """
            SELECT pa.resolved_by_id AS approver_id,
                   approver.name AS approver_name,
                   COUNT(*) AS approved_appeal_count,
                   MAX(pa.resolved_at) AS latest_resolved_at
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            JOIN customer approver ON pa.resolved_by_id = approver.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_by_id IS NOT NULL
              AND pa.resolved_at >= :monthStart
              AND pa.resolved_at < :monthEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
              AND approver.deleted_at IS NULL
            GROUP BY pa.resolved_by_id, approver.name
            ORDER BY approved_appeal_count DESC, latest_resolved_at DESC, approver_id ASC
            LIMIT 1
            """;

    private static final String READ_RECENT_ACCEPTED_APPEALS_SQL =
            """
            SELECT pa.id AS appeal_id,
                   pa.requester_id,
                   requester.name AS requester_name,
                   pa.request_reason,
                   pa.resolved_at
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            LEFT JOIN customer requester
              ON pa.requester_id = requester.id
             AND requester.deleted_at IS NULL
            WHERE pas.family_id = :familyId
              AND pa.resolved_by_id = :approverId
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_at >= :monthStart
              AND pa.resolved_at < :monthEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            ORDER BY pa.resolved_at DESC, pa.id DESC
            LIMIT 3
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MonthlyFamilyRecapSourceMetrics aggregate(Long familyId, LocalDate targetMonth) {
        LocalDate monthEndExclusiveDate = targetMonth.plusMonths(1);
        LocalDateTime monthStart = targetMonth.atStartOfDay();
        LocalDateTime monthEndExclusive = monthEndExclusiveDate.atStartOfDay();

        // full week 조회와 carry-in 조회에 함께 쓰는 월 경계값
        MapSqlParameterSource monthlyParams =
                new MapSqlParameterSource()
                        .addValue("familyId", familyId)
                        .addValue("monthStartDate", Date.valueOf(targetMonth))
                        .addValue("monthEndExclusiveDate", Date.valueOf(monthEndExclusiveDate))
                        // 월 안에 완전히 들어오는 마지막 주 시작일
                        .addValue(
                                "lastFullWeekStartDate",
                                Date.valueOf(monthEndExclusiveDate.minusDays(7)))
                        // 월과 겹칠 수 있는 최근 weekly snapshot 탐색 시작점
                        .addValue("overlapStartDate", Date.valueOf(targetMonth.minusDays(6)))
                        .addValue("monthStart", Timestamp.valueOf(monthStart))
                        .addValue("monthEndExclusive", Timestamp.valueOf(monthEndExclusive));

        List<Map<String, Object>> fullWeekRows =
                jdbcTemplate.queryForList(READ_FULL_WEEKLY_RECAP_ROWS_SQL, monthlyParams);
        // 월 내부 full week row만 weekly 합산에 사용
        List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots = readFullWeekSnapshots(fullWeekRows);

        return new MonthlyFamilyRecapSourceMetrics(
                fullWeekSnapshots,
                readQuotaSnapshot(monthlyParams),
                readPartialUsageMetrics(familyId, targetMonth),
                readMissionSummary(familyId, targetMonth, fullWeekSnapshots),
                readAppealSummary(familyId, targetMonth, fullWeekSnapshots),
                readMissionCarryInCount(monthlyParams),
                readAppealCarryInCount(monthlyParams),
                readAppealHighlights(monthlyParams));
    }

    private List<MonthlyWeeklyRecapSnapshot> readFullWeekSnapshots(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(
                        row ->
                                new MonthlyWeeklyRecapSnapshot(
                                        toLocalDate(row.get("week_start_date")),
                                        toLong(row.get("total_used_bytes")),
                                        toLong(row.get("total_quota_bytes")),
                                        toJsonString(row.get("usage_by_weekday")),
                                        toJsonString(row.get("peak_usage")),
                                        toInt(row.get("mission_created_count")),
                                        toInt(row.get("mission_completed_count")),
                                        toInt(row.get("mission_rejected_count")),
                                        toInt(row.get("total_appeal_count")),
                                        toInt(row.get(APPROVED_APPEAL_COUNT)),
                                        toInt(row.get("rejected_appeal_count"))))
                .toList();
    }

    private long readQuotaSnapshot(MapSqlParameterSource params) {
        // quota는 합산하지 않고 월과 겹치는 가장 최근 weekly snapshot 1건만 사용
        Long quotaFromWeekly = readNullableLong(READ_LATEST_QUOTA_SNAPSHOT_FROM_WEEKLY_SQL, params);
        if (quotaFromWeekly != null) {
            return quotaFromWeekly;
        }

        // 주간 스냅샷이 없으면 family 현재 quota로 fallback
        Long quotaFromFamily = readNullableLong(READ_FAMILY_QUOTA_BYTES_SQL, params);
        return quotaFromFamily == null ? 0L : quotaFromFamily;
    }

    private MonthlyUsageSupplementMetrics readPartialUsageMetrics(
            Long familyId, LocalDate targetMonth) {
        List<DateRange> partialRanges = resolvePartialRanges(targetMonth);
        if (partialRanges.isEmpty()) {
            return MonthlyUsageSupplementMetrics.empty();
        }

        long totalUsedBytes = 0L;
        Map<String, Long> usageBytesByWeekday = initWeekdayUsageMap();
        Map<Integer, Long> usageBytesByHour = new LinkedHashMap<>();

        // 월 경계를 걸치는 좌/우 partial week만 raw 사용량으로 보강
        for (DateRange range : partialRanges) {
            MapSqlParameterSource rangeParams = buildRangeParams(familyId, range);

            totalUsedBytes += readLong(READ_TOTAL_USED_BYTES_IN_RANGE_SQL, rangeParams);

            for (Map<String, Object> row :
                    jdbcTemplate.queryForList(READ_USAGE_BY_WEEKDAY_IN_RANGE_SQL, rangeParams)) {
                String weekday = resolveWeekdayKey(toInt(row.get("day_of_week")));
                long bytes = toLong(row.get("total_bytes"));
                usageBytesByWeekday.put(
                        weekday, usageBytesByWeekday.getOrDefault(weekday, 0L) + bytes);
            }

            for (Map<String, Object> row :
                    jdbcTemplate.queryForList(READ_USAGE_BY_HOUR_IN_RANGE_SQL, rangeParams)) {
                int startHour = toInt(row.get("start_hour"));
                long bytes = toLong(row.get("total_bytes"));
                usageBytesByHour.put(
                        startHour, usageBytesByHour.getOrDefault(startHour, 0L) + bytes);
            }
        }

        MonthlyUsagePeakCandidate peakUsageCandidate = MonthlyUsagePeakCandidate.empty();
        for (Map.Entry<Integer, Long> entry : usageBytesByHour.entrySet()) {
            MonthlyUsagePeakCandidate candidate =
                    new MonthlyUsagePeakCandidate(
                            entry.getKey(), entry.getKey() + 1, entry.getValue());
            if (candidate.isBetterThan(peakUsageCandidate)) {
                peakUsageCandidate = candidate;
            }
        }

        return new MonthlyUsageSupplementMetrics(
                totalUsedBytes, usageBytesByWeekday, peakUsageCandidate);
    }

    private List<DateRange> resolvePartialRanges(LocalDate targetMonth) {
        LocalDate monthStartDate = targetMonth;
        LocalDate monthEndExclusiveDate = targetMonth.plusMonths(1);
        LocalDate firstFullWeekStart =
                monthStartDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate rightPartialWeekStart =
                monthEndExclusiveDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // 월 바깥으로 걸친 구간만 보강
        List<DateRange> ranges = new ArrayList<>();
        if (monthStartDate.isBefore(firstFullWeekStart)) {
            // 좌측 partial
            ranges.add(
                    new DateRange(
                            monthStartDate.atStartOfDay(), firstFullWeekStart.atStartOfDay()));
        }
        if (rightPartialWeekStart.isBefore(monthEndExclusiveDate)) {
            // 우측 partial
            ranges.add(
                    new DateRange(
                            rightPartialWeekStart.atStartOfDay(),
                            monthEndExclusiveDate.atStartOfDay()));
        }
        return ranges;
    }

    private MonthlyMissionSummary readMissionSummary(
            Long familyId,
            LocalDate targetMonth,
            List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots) {
        // 월 경계 partial만 raw로 보강
        MonthlyMissionSummary partialMissionSummary =
                readPartialMissionSummary(familyId, targetMonth);

        int totalMissionCount = partialMissionSummary.totalMissionCount();
        int completedMissionCount = partialMissionSummary.completedMissionCount();
        int rejectedRequestCount = partialMissionSummary.rejectedRequestCount();

        // 월 내부 full week는 weekly snapshot 재사용
        for (MonthlyWeeklyRecapSnapshot snapshot : fullWeekSnapshots) {
            totalMissionCount += snapshot.missionCreatedCount();
            completedMissionCount += snapshot.missionCompletedCount();
            rejectedRequestCount += snapshot.missionRejectedCount();
        }

        return new MonthlyMissionSummary(
                totalMissionCount, completedMissionCount, rejectedRequestCount);
    }

    private MonthlyAppealSummary readAppealSummary(
            Long familyId,
            LocalDate targetMonth,
            List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots) {
        // 월 경계 partial만 raw로 보강
        MonthlyAppealSummary partialAppealSummary = readPartialAppealSummary(familyId, targetMonth);

        int totalAppeals = partialAppealSummary.totalAppeals();
        int approvedAppeals = partialAppealSummary.approvedAppeals();
        int rejectedAppeals = partialAppealSummary.rejectedAppeals();

        // 월 내부 full week는 weekly snapshot 재사용
        for (MonthlyWeeklyRecapSnapshot snapshot : fullWeekSnapshots) {
            totalAppeals += snapshot.totalAppealCount();
            approvedAppeals += snapshot.approvedAppealCount();
            rejectedAppeals += snapshot.rejectedAppealCount();
        }

        return new MonthlyAppealSummary(totalAppeals, approvedAppeals, rejectedAppeals);
    }

    private MonthlyMissionSummary readPartialMissionSummary(Long familyId, LocalDate targetMonth) {
        List<DateRange> partialRanges = resolvePartialRanges(targetMonth);
        if (partialRanges.isEmpty()) {
            // full week만으로 월 집계 가능
            return MonthlyMissionSummary.empty();
        }

        int totalMissionCount = 0;
        int completedMissionCount = 0;
        int rejectedRequestCount = 0;

        for (DateRange range : partialRanges) {
            MapSqlParameterSource rangeParams = buildRangeParams(familyId, range);
            totalMissionCount += readInt(READ_MISSION_CREATED_COUNT_IN_RANGE_SQL, rangeParams);
            completedMissionCount +=
                    readInt(READ_MISSION_COMPLETED_COUNT_IN_RANGE_SQL, rangeParams);
            rejectedRequestCount += readInt(READ_MISSION_REJECTED_COUNT_IN_RANGE_SQL, rangeParams);
        }

        return new MonthlyMissionSummary(
                totalMissionCount, completedMissionCount, rejectedRequestCount);
    }

    private MonthlyAppealSummary readPartialAppealSummary(Long familyId, LocalDate targetMonth) {
        List<DateRange> partialRanges = resolvePartialRanges(targetMonth);
        if (partialRanges.isEmpty()) {
            // full week만으로 월 집계 가능
            return MonthlyAppealSummary.empty();
        }

        int totalAppeals = 0;
        int approvedAppeals = 0;
        int rejectedAppeals = 0;

        for (DateRange range : partialRanges) {
            MapSqlParameterSource rangeParams = buildRangeParams(familyId, range);
            totalAppeals += readInt(READ_TOTAL_APPEAL_COUNT_IN_RANGE_SQL, rangeParams);
            approvedAppeals += readInt(READ_APPROVED_APPEAL_COUNT_IN_RANGE_SQL, rangeParams);
            rejectedAppeals += readInt(READ_REJECTED_APPEAL_COUNT_IN_RANGE_SQL, rangeParams);
        }

        return new MonthlyAppealSummary(totalAppeals, approvedAppeals, rejectedAppeals);
    }

    private int readMissionCarryInCount(MapSqlParameterSource params) {
        // 월초 시점 미완료 미션 수
        return readInt(READ_MISSION_CARRY_IN_COUNT_SQL, params);
    }

    private int readAppealCarryInCount(MapSqlParameterSource params) {
        // 월초 시점 미해결 NORMAL 이의제기 수
        return readInt(READ_APPEAL_CARRY_IN_COUNT_SQL, params);
    }

    private MonthlyAppealHighlights readAppealHighlights(MapSqlParameterSource params) {
        MonthlyAppealHighlights.TopSuccessfulRequester topSuccessfulRequester =
                readTopSuccessfulRequester(params);
        MonthlyAppealHighlights.TopAcceptedApprover topAcceptedApprover =
                readTopAcceptedApprover(params);
        return new MonthlyAppealHighlights(topSuccessfulRequester, topAcceptedApprover);
    }

    private MonthlyAppealHighlights.TopSuccessfulRequester readTopSuccessfulRequester(
            MapSqlParameterSource params) {
        try {
            Map<String, Object> row =
                    jdbcTemplate.queryForMap(READ_TOP_SUCCESSFUL_REQUESTER_SQL, params);
            Long requesterId = toLongObject(row.get("requester_id"));

            // 대표 requester를 고른 뒤 최신 승인 이력 3건을 requestedAt 기준으로 붙임
            // 정렬은 resolvedAt 기준
            MapSqlParameterSource recentParams = copyParams(params);
            recentParams.addValue("requesterId", requesterId);
            List<MonthlyAppealHighlights.RecentApprovedAppeal> recentApprovedAppeals =
                    jdbcTemplate
                            .queryForList(READ_RECENT_APPROVED_APPEALS_SQL, recentParams)
                            .stream()
                            .map(
                                    recentRow ->
                                            new MonthlyAppealHighlights.RecentApprovedAppeal(
                                                    toLongObject(recentRow.get("appeal_id")),
                                                    toLongObject(recentRow.get("approver_id")),
                                                    toNullableString(
                                                            recentRow.get("approver_name")),
                                                    toNullableString(
                                                            recentRow.get("request_reason")),
                                                    toIsoDateTime(recentRow.get("requested_at"))))
                            .toList();

            return new MonthlyAppealHighlights.TopSuccessfulRequester(
                    requesterId,
                    toNullableString(row.get("requester_name")),
                    toInt(row.get(APPROVED_APPEAL_COUNT)),
                    recentApprovedAppeals);
        } catch (EmptyResultDataAccessException exception) {
            return MonthlyAppealHighlights.TopSuccessfulRequester.empty();
        }
    }

    private MonthlyAppealHighlights.TopAcceptedApprover readTopAcceptedApprover(
            MapSqlParameterSource params) {
        try {
            Map<String, Object> row =
                    jdbcTemplate.queryForMap(READ_TOP_ACCEPTED_APPROVER_SQL, params);
            Long approverId = toLongObject(row.get("approver_id"));

            // 대표 approver를 고른 뒤 최신 수락 이력 3건을 resolvedAt 기준으로 붙임
            MapSqlParameterSource recentParams = copyParams(params);
            recentParams.addValue("approverId", approverId);
            List<MonthlyAppealHighlights.RecentAcceptedAppeal> recentAcceptedAppeals =
                    jdbcTemplate
                            .queryForList(READ_RECENT_ACCEPTED_APPEALS_SQL, recentParams)
                            .stream()
                            .map(
                                    recentRow ->
                                            new MonthlyAppealHighlights.RecentAcceptedAppeal(
                                                    toLongObject(recentRow.get("appeal_id")),
                                                    toLongObject(recentRow.get("requester_id")),
                                                    toNullableString(
                                                            recentRow.get("requester_name")),
                                                    toNullableString(
                                                            recentRow.get("request_reason")),
                                                    toIsoDateTime(recentRow.get("resolved_at"))))
                            .toList();

            return new MonthlyAppealHighlights.TopAcceptedApprover(
                    approverId,
                    toNullableString(row.get("approver_name")),
                    toInt(row.get(APPROVED_APPEAL_COUNT)),
                    recentAcceptedAppeals);
        } catch (EmptyResultDataAccessException exception) {
            return MonthlyAppealHighlights.TopAcceptedApprover.empty();
        }
    }

    private MapSqlParameterSource copyParams(MapSqlParameterSource params) {
        MapSqlParameterSource copied = new MapSqlParameterSource();
        for (String name : params.getParameterNames()) {
            copied.addValue(name, params.getValue(name));
        }
        return copied;
    }

    private MapSqlParameterSource buildRangeParams(Long familyId, DateRange range) {
        return new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("rangeStart", Timestamp.valueOf(range.startInclusive()))
                .addValue("rangeEndExclusive", Timestamp.valueOf(range.endExclusive()));
    }

    private Map<String, Long> initWeekdayUsageMap() {
        Map<String, Long> usageByWeekday = new LinkedHashMap<>();
        usageByWeekday.put("monday", 0L);
        usageByWeekday.put("tuesday", 0L);
        usageByWeekday.put("wednesday", 0L);
        usageByWeekday.put("thursday", 0L);
        usageByWeekday.put("friday", 0L);
        usageByWeekday.put("saturday", 0L);
        usageByWeekday.put("sunday", 0L);
        return usageByWeekday;
    }

    private long readLong(String sql, MapSqlParameterSource params) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
            return value == null ? 0L : value;
        } catch (EmptyResultDataAccessException exception) {
            return 0L;
        }
    }

    private int readInt(String sql, MapSqlParameterSource params) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, params, Integer.class);
            return value == null ? 0 : value;
        } catch (EmptyResultDataAccessException exception) {
            return 0;
        }
    }

    private Long readNullableLong(String sql, MapSqlParameterSource params) {
        try {
            return jdbcTemplate.queryForObject(sql, params, Long.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof Date dateValue) {
            return dateValue.toLocalDate();
        }
        if (value instanceof Timestamp timestampValue) {
            return timestampValue.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private String toJsonString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int toInt(Object value) {
        return value instanceof Number numberValue ? numberValue.intValue() : 0;
    }

    private long toLong(Object value) {
        return value instanceof Number numberValue ? numberValue.longValue() : 0L;
    }

    private Long toLongObject(Object value) {
        return value == null ? null : toLong(value);
    }

    private String toNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toIsoDateTime(Object value) {
        if (value instanceof Timestamp timestampValue) {
            return timestampValue.toLocalDateTime().format(ISO_DATE_TIME);
        }
        if (value instanceof Date dateValue) {
            return dateValue.toLocalDate().atStartOfDay().format(ISO_DATE_TIME);
        }
        return value == null ? null : String.valueOf(value);
    }

    private String resolveWeekdayKey(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "sunday";
            case 2 -> "monday";
            case 3 -> "tuesday";
            case 4 -> "wednesday";
            case 5 -> "thursday";
            case 6 -> "friday";
            case 7 -> "saturday";
            default -> throw new IllegalArgumentException("Unexpected day_of_week: " + dayOfWeek);
        };
    }

    private record DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {}
}



