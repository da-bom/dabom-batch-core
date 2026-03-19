package com.template.worker.jobs.recap.monthly.query;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;

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
    private static final String PARTIAL_EVENT_TIME_CONDITION =
            buildPartialRangeCondition("event_time");
    private static final String PARTIAL_CREATED_AT_CONDITION =
            buildPartialRangeCondition("created_at");
    private static final String PARTIAL_COMPLETED_AT_CONDITION =
            buildPartialRangeCondition("completed_at");
    private static final String PARTIAL_MR_RESOLVED_AT_CONDITION =
            buildPartialRangeCondition("mr.resolved_at");
    private static final String PARTIAL_APPEAL_CREATED_AT_CONDITION =
            buildPartialRangeCondition("pa.created_at");
    private static final String PARTIAL_APPEAL_RESOLVED_AT_CONDITION =
            buildPartialRangeCondition("pa.resolved_at");

    private static final String READ_FULL_WEEKLY_RECAP_ROWS_SQL =
            """
            SELECT family_id, week_start_date, total_used_bytes, total_quota_bytes, usage_by_weekday,
                   peak_usage, mission_created_count, mission_completed_count,
                   mission_rejected_count, total_appeal_count, approved_appeal_count,
                   rejected_appeal_count
            FROM family_recap_weekly
            WHERE family_id IN (:familyIds)
              AND week_start_date >= :monthStartDate
              AND week_start_date <= :lastFullWeekStartDate
            ORDER BY family_id ASC, week_start_date ASC
            """;

    private static final String READ_WEEKLY_QUOTA_ROWS_SQL =
            """
            SELECT family_id, week_start_date, total_quota_bytes
            FROM family_recap_weekly
            WHERE family_id IN (:familyIds)
              AND week_start_date < :monthEndExclusiveDate
              AND week_start_date >= :overlapStartDate
            ORDER BY family_id ASC, week_start_date DESC
            """;

    private static final String READ_FAMILY_QUOTA_BYTES_SQL =
            """
            SELECT family_id, total_quota_bytes
            FROM family_quota
            WHERE family_id IN (:familyIds)
              AND current_month = :monthStartDate
              AND deleted_at IS NULL
            """;

    private static final String READ_TOTAL_USED_BYTES_IN_RANGE_SQL =
            """
            SELECT family_id, COALESCE(SUM(bytes_used), 0) AS total_used_bytes
            FROM usage_record
            WHERE family_id IN (:familyIds)
              AND """
                    + PARTIAL_EVENT_TIME_CONDITION
                    + """
              AND deleted_at IS NULL
            GROUP BY family_id
            """;

    private static final String READ_USAGE_BY_WEEKDAY_IN_RANGE_SQL =
            """
            SELECT family_id, DAYOFWEEK(event_time) AS day_of_week, COALESCE(SUM(bytes_used), 0) AS total_bytes
            FROM usage_record
            WHERE family_id IN (:familyIds)
              AND """
                    + PARTIAL_EVENT_TIME_CONDITION
                    + """
              AND deleted_at IS NULL
            GROUP BY family_id, DAYOFWEEK(event_time)
            """;

    private static final String READ_USAGE_BY_HOUR_IN_RANGE_SQL =
            """
            SELECT family_id, HOUR(event_time) AS start_hour, COALESCE(SUM(bytes_used), 0) AS total_bytes
            FROM usage_record
            WHERE family_id IN (:familyIds)
              AND """
                    + PARTIAL_EVENT_TIME_CONDITION
                    + """
              AND deleted_at IS NULL
            GROUP BY family_id, HOUR(event_time)
            """;

    private static final String READ_MISSION_CREATED_COUNT_IN_RANGE_SQL =
            """
            SELECT family_id, COUNT(*) AS mission_created_count
            FROM mission_item
            WHERE family_id IN (:familyIds)
              AND """
                    + PARTIAL_CREATED_AT_CONDITION
                    + """
              AND deleted_at IS NULL
            GROUP BY family_id
            """;

    private static final String READ_MISSION_COMPLETED_COUNT_IN_RANGE_SQL =
            """
            SELECT family_id, COUNT(*) AS mission_completed_count
            FROM mission_item
            WHERE family_id IN (:familyIds)
              AND status = 'COMPLETED'
              AND """
                    + PARTIAL_COMPLETED_AT_CONDITION
                    + """
              AND deleted_at IS NULL
            GROUP BY family_id
            """;

    private static final String READ_MISSION_REJECTED_COUNT_IN_RANGE_SQL =
            """
            SELECT mi.family_id, COUNT(*) AS mission_rejected_count
            FROM mission_request mr
            JOIN mission_item mi ON mr.mission_item_id = mi.id
            WHERE mi.family_id IN (:familyIds)
              AND mr.status = 'REJECTED'
              AND """
                    + PARTIAL_MR_RESOLVED_AT_CONDITION
                    + """
              AND mr.deleted_at IS NULL
              AND mi.deleted_at IS NULL
            GROUP BY mi.family_id
            """;

    private static final String READ_MISSION_CARRY_IN_COUNT_SQL =
            """
            SELECT mi.family_id, COUNT(*) AS mission_carry_in_count
            FROM mission_item mi
            WHERE mi.family_id IN (:familyIds)
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
            GROUP BY mi.family_id
            """;

    private static final String READ_TOTAL_APPEAL_COUNT_IN_RANGE_SQL =
            """
            SELECT pas.family_id, COUNT(*) AS total_appeal_count
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND """
                    + PARTIAL_APPEAL_CREATED_AT_CONDITION
                    + """
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            GROUP BY pas.family_id
            """;

    private static final String READ_APPROVED_APPEAL_COUNT_IN_RANGE_SQL =
            """
            SELECT pas.family_id, COUNT(*) AS approved_appeal_count
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND """
                    + PARTIAL_APPEAL_RESOLVED_AT_CONDITION
                    + """
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            GROUP BY pas.family_id
            """;

    private static final String READ_REJECTED_APPEAL_COUNT_IN_RANGE_SQL =
            """
            SELECT pas.family_id, COUNT(*) AS rejected_appeal_count
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND pa.status = 'REJECTED'
              AND """
                    + PARTIAL_APPEAL_RESOLVED_AT_CONDITION
                    + """
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            GROUP BY pas.family_id
            """;

    private static final String READ_APPEAL_CARRY_IN_COUNT_SQL =
            """
            SELECT pas.family_id, COUNT(*) AS appeal_carry_in_count
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND pa.created_at < :monthStart
              AND (pa.resolved_at IS NULL OR pa.resolved_at >= :monthStart)
              AND (pa.cancelled_at IS NULL OR pa.cancelled_at >= :monthStart)
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            GROUP BY pas.family_id
            """;

    private static final String READ_APPROVED_APPEAL_EVENTS_SQL =
            """
            SELECT pas.family_id, pa.id AS appeal_id, pa.requester_id, requester.name AS requester_name,
                   pa.resolved_by_id AS approver_id, approver.name AS approver_name, pa.request_reason,
                   pa.created_at AS requested_at, pa.resolved_at
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            LEFT JOIN customer requester ON pa.requester_id = requester.id AND requester.deleted_at IS NULL
            LEFT JOIN customer approver ON pa.resolved_by_id = approver.id AND approver.deleted_at IS NULL
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_at >= :monthStart
              AND pa.resolved_at < :monthEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            ORDER BY pas.family_id ASC, pa.resolved_at DESC, pa.id DESC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MonthlyFamilyRecapSourceMetrics aggregate(Long familyId, LocalDate targetMonth) {
        return aggregate(List.of(familyId), targetMonth).get(familyId);
    }

    public Map<Long, MonthlyFamilyRecapSourceMetrics> aggregate(
            List<Long> familyIds, LocalDate targetMonth) {
        if (familyIds.isEmpty()) {
            return Map.of();
        }

        LocalDate monthEndExclusiveDate = targetMonth.plusMonths(1);
        LocalDateTime monthStart = targetMonth.atStartOfDay();
        LocalDateTime monthEndExclusive = monthEndExclusiveDate.atStartOfDay();

        Map<Long, MutableMonthlyMetrics> metricsByFamily = initMetrics(familyIds);
        MapSqlParameterSource monthlyParams =
                new MapSqlParameterSource()
                        .addValue("familyIds", familyIds)
                        .addValue("monthStartDate", Date.valueOf(targetMonth))
                        .addValue("monthEndExclusiveDate", Date.valueOf(monthEndExclusiveDate))
                        .addValue(
                                "lastFullWeekStartDate",
                                Date.valueOf(monthEndExclusiveDate.minusDays(7)))
                        .addValue("overlapStartDate", Date.valueOf(targetMonth.minusDays(6)))
                        .addValue("monthStart", Timestamp.valueOf(monthStart))
                        .addValue("monthEndExclusive", Timestamp.valueOf(monthEndExclusive));

        applyFullWeekSnapshots(monthlyParams, metricsByFamily);
        applyQuotaSnapshot(monthlyParams, metricsByFamily);
        applyFallbackFamilyQuota(monthlyParams, metricsByFamily);

        MapSqlParameterSource partialRangeParams = buildPartialRangeParams(familyIds, targetMonth);
        if (hasPartialRange(partialRangeParams)) {
            applyPartialUsage(partialRangeParams, metricsByFamily);
            applyPartialMissionSummary(partialRangeParams, metricsByFamily);
            applyPartialAppealSummary(partialRangeParams, metricsByFamily);
        }

        applyCount(
                READ_MISSION_CARRY_IN_COUNT_SQL,
                "mission_carry_in_count",
                monthlyParams,
                metricsByFamily,
                MutableMonthlyMetrics::setMissionCarryInCount);
        applyCount(
                READ_APPEAL_CARRY_IN_COUNT_SQL,
                "appeal_carry_in_count",
                monthlyParams,
                metricsByFamily,
                MutableMonthlyMetrics::setAppealCarryInCount);
        applyApprovedAppealEvents(monthlyParams, metricsByFamily);

        Map<Long, MonthlyFamilyRecapSourceMetrics> result = new LinkedHashMap<>();
        for (Long familyId : familyIds) {
            MutableMonthlyMetrics metrics = metricsByFamily.get(familyId);
            result.put(
                    familyId,
                    new MonthlyFamilyRecapSourceMetrics(
                            List.copyOf(metrics.fullWeekSnapshots),
                            metrics.totalQuotaBytes,
                            metrics.toPartialUsageMetrics(),
                            metrics.toMissionSummary(),
                            metrics.toAppealSummary(),
                            metrics.missionCarryInCount,
                            metrics.appealCarryInCount,
                            metrics.toAppealHighlights()));
        }
        return result;
    }

    private Map<Long, MutableMonthlyMetrics> initMetrics(List<Long> familyIds) {
        Map<Long, MutableMonthlyMetrics> metricsByFamily = new LinkedHashMap<>();
        for (Long familyId : familyIds) {
            metricsByFamily.put(familyId, new MutableMonthlyMetrics());
        }
        return metricsByFamily;
    }

    private void applyFullWeekSnapshots(
            MapSqlParameterSource params, Map<Long, MutableMonthlyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_FULL_WEEKLY_RECAP_ROWS_SQL, params)
                .forEach(
                        row ->
                                metricsByFamily
                                        .get(toLong(row.get("family_id")))
                                        .fullWeekSnapshots
                                        .add(
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
                                                        toInt(row.get("rejected_appeal_count")))));
    }

    private void applyQuotaSnapshot(
            MapSqlParameterSource params, Map<Long, MutableMonthlyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_WEEKLY_QUOTA_ROWS_SQL, params)
                .forEach(
                        row -> {
                            MutableMonthlyMetrics metrics =
                                    metricsByFamily.get(toLong(row.get("family_id")));
                            if (!metrics.quotaResolved) {
                                metrics.totalQuotaBytes = toLong(row.get("total_quota_bytes"));
                                metrics.quotaResolved = true;
                            }
                        });
    }

    private void applyFallbackFamilyQuota(
            MapSqlParameterSource params, Map<Long, MutableMonthlyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_FAMILY_QUOTA_BYTES_SQL, params)
                .forEach(
                        row -> {
                            MutableMonthlyMetrics metrics =
                                    metricsByFamily.get(toLong(row.get("family_id")));
                            if (!metrics.quotaResolved) {
                                metrics.totalQuotaBytes = toLong(row.get("total_quota_bytes"));
                                metrics.quotaResolved = true;
                            }
                        });
    }

    private void applyPartialUsage(
            MapSqlParameterSource params, Map<Long, MutableMonthlyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_TOTAL_USED_BYTES_IN_RANGE_SQL, params)
                .forEach(
                        row ->
                                metricsByFamily.get(toLong(row.get("family_id")))
                                                .partialTotalUsedBytes +=
                                        toLong(row.get("total_used_bytes")));

        jdbcTemplate
                .queryForList(READ_USAGE_BY_WEEKDAY_IN_RANGE_SQL, params)
                .forEach(
                        row -> {
                            MutableMonthlyMetrics metrics =
                                    metricsByFamily.get(toLong(row.get("family_id")));
                            String weekday = resolveWeekdayKey(toInt(row.get("day_of_week")));
                            metrics.partialUsageByWeekday.put(
                                    weekday,
                                    metrics.partialUsageByWeekday.getOrDefault(weekday, 0L)
                                            + toLong(row.get("total_bytes")));
                        });

        jdbcTemplate
                .queryForList(READ_USAGE_BY_HOUR_IN_RANGE_SQL, params)
                .forEach(
                        row -> {
                            MutableMonthlyMetrics metrics =
                                    metricsByFamily.get(toLong(row.get("family_id")));
                            int startHour = toInt(row.get("start_hour"));
                            metrics.partialUsageByHour.put(
                                    startHour,
                                    metrics.partialUsageByHour.getOrDefault(startHour, 0L)
                                            + toLong(row.get("total_bytes")));
                        });
    }

    private void applyPartialMissionSummary(
            MapSqlParameterSource params, Map<Long, MutableMonthlyMetrics> metricsByFamily) {
        applyCount(
                READ_MISSION_CREATED_COUNT_IN_RANGE_SQL,
                "mission_created_count",
                params,
                metricsByFamily,
                MutableMonthlyMetrics::setPartialMissionCreatedCount);
        applyCount(
                READ_MISSION_COMPLETED_COUNT_IN_RANGE_SQL,
                "mission_completed_count",
                params,
                metricsByFamily,
                MutableMonthlyMetrics::setPartialMissionCompletedCount);
        applyCount(
                READ_MISSION_REJECTED_COUNT_IN_RANGE_SQL,
                "mission_rejected_count",
                params,
                metricsByFamily,
                MutableMonthlyMetrics::setPartialMissionRejectedCount);
    }

    private void applyPartialAppealSummary(
            MapSqlParameterSource params, Map<Long, MutableMonthlyMetrics> metricsByFamily) {
        applyCount(
                READ_TOTAL_APPEAL_COUNT_IN_RANGE_SQL,
                "total_appeal_count",
                params,
                metricsByFamily,
                MutableMonthlyMetrics::setPartialTotalAppeals);
        applyCount(
                READ_APPROVED_APPEAL_COUNT_IN_RANGE_SQL,
                APPROVED_APPEAL_COUNT,
                params,
                metricsByFamily,
                MutableMonthlyMetrics::setPartialApprovedAppeals);
        applyCount(
                READ_REJECTED_APPEAL_COUNT_IN_RANGE_SQL,
                "rejected_appeal_count",
                params,
                metricsByFamily,
                MutableMonthlyMetrics::setPartialRejectedAppeals);
    }

    private void applyApprovedAppealEvents(
            MapSqlParameterSource params, Map<Long, MutableMonthlyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_APPROVED_APPEAL_EVENTS_SQL, params)
                .forEach(
                        row ->
                                metricsByFamily
                                        .get(toLong(row.get("family_id")))
                                        .approvedAppealEvents
                                        .add(
                                                new ApprovedAppealEvent(
                                                        toLongObject(row.get("appeal_id")),
                                                        toLongObject(row.get("requester_id")),
                                                        toNullableString(row.get("requester_name")),
                                                        toLongObject(row.get("approver_id")),
                                                        toNullableString(row.get("approver_name")),
                                                        toNullableString(row.get("request_reason")),
                                                        toIsoDateTime(row.get("requested_at")),
                                                        toTimestamp(row.get("resolved_at")))));
    }

    private void applyCount(
            String sql,
            String countColumn,
            MapSqlParameterSource params,
            Map<Long, MutableMonthlyMetrics> metricsByFamily,
            ObjIntConsumer<MutableMonthlyMetrics> countSetter) {
        jdbcTemplate
                .queryForList(sql, params)
                .forEach(
                        row ->
                                countSetter.accept(
                                        metricsByFamily.get(toLong(row.get("family_id"))),
                                        toInt(row.get(countColumn))));
    }

    private boolean hasPartialRange(MapSqlParameterSource params) {
        return params.getValue("leftRangeStart") != null
                || params.getValue("rightRangeStart") != null;
    }

    private MapSqlParameterSource buildPartialRangeParams(
            List<Long> familyIds, LocalDate targetMonth) {
        List<DateRange> ranges = resolvePartialRanges(targetMonth);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("familyIds", familyIds);
        addTimestamp(
                params, "leftRangeStart", ranges.isEmpty() ? null : ranges.get(0).startInclusive());
        addTimestamp(
                params,
                "leftRangeEndExclusive",
                ranges.isEmpty() ? null : ranges.get(0).endExclusive());
        addTimestamp(
                params,
                "rightRangeStart",
                ranges.size() > 1 ? ranges.get(1).startInclusive() : null);
        addTimestamp(
                params,
                "rightRangeEndExclusive",
                ranges.size() > 1 ? ranges.get(1).endExclusive() : null);
        return params;
    }

    private List<DateRange> resolvePartialRanges(LocalDate targetMonth) {
        LocalDate monthStartDate = targetMonth;
        LocalDate monthEndExclusiveDate = targetMonth.plusMonths(1);
        LocalDate firstFullWeekStart =
                monthStartDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate rightPartialWeekStart =
                monthEndExclusiveDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<DateRange> ranges = new ArrayList<>();
        if (monthStartDate.isBefore(firstFullWeekStart)) {
            ranges.add(
                    new DateRange(
                            monthStartDate.atStartOfDay(), firstFullWeekStart.atStartOfDay()));
        }
        if (rightPartialWeekStart.isBefore(monthEndExclusiveDate)) {
            ranges.add(
                    new DateRange(
                            rightPartialWeekStart.atStartOfDay(),
                            monthEndExclusiveDate.atStartOfDay()));
        }
        return ranges;
    }

    private void addTimestamp(MapSqlParameterSource params, String key, LocalDateTime value) {
        params.addValue(key, value == null ? null : Timestamp.valueOf(value), Types.TIMESTAMP);
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

    private static String buildPartialRangeCondition(String column) {
        return """
               ((:leftRangeStart IS NOT NULL AND %1$s >= :leftRangeStart AND %1$s < :leftRangeEndExclusive)
                 OR (:rightRangeStart IS NOT NULL AND %1$s >= :rightRangeStart AND %1$s < :rightRangeEndExclusive))
               """
                .formatted(column);
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

    private Timestamp toTimestamp(Object value) {
        if (value instanceof Timestamp timestampValue) {
            return timestampValue;
        }
        if (value instanceof Date dateValue) {
            return Timestamp.valueOf(dateValue.toLocalDate().atStartOfDay());
        }
        return value == null ? null : Timestamp.valueOf(String.valueOf(value));
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
        Timestamp timestamp = toTimestamp(value);
        return timestamp == null ? null : timestamp.toLocalDateTime().format(ISO_DATE_TIME);
    }

    private record DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {}

    private record ApprovedAppealEvent(
            Long appealId,
            Long requesterId,
            String requesterName,
            Long approverId,
            String approverName,
            String requestReason,
            String requestedAt,
            Timestamp resolvedAt) {}

    private static final class MutableMonthlyMetrics {

        private final List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots = new ArrayList<>();
        private final Map<String, Long> partialUsageByWeekday = initWeekdayUsageMap();
        private final Map<Integer, Long> partialUsageByHour = new HashMap<>();
        private final List<ApprovedAppealEvent> approvedAppealEvents = new ArrayList<>();

        private long totalQuotaBytes;
        private boolean quotaResolved;
        private long partialTotalUsedBytes;
        private int partialMissionCreatedCount;
        private int partialMissionCompletedCount;
        private int partialMissionRejectedCount;
        private int partialTotalAppeals;
        private int partialApprovedAppeals;
        private int partialRejectedAppeals;
        private int missionCarryInCount;
        private int appealCarryInCount;

        private void setPartialMissionCreatedCount(int value) {
            partialMissionCreatedCount = value;
        }

        private void setPartialMissionCompletedCount(int value) {
            partialMissionCompletedCount = value;
        }

        private void setPartialMissionRejectedCount(int value) {
            partialMissionRejectedCount = value;
        }

        private void setPartialTotalAppeals(int value) {
            partialTotalAppeals = value;
        }

        private void setPartialApprovedAppeals(int value) {
            partialApprovedAppeals = value;
        }

        private void setPartialRejectedAppeals(int value) {
            partialRejectedAppeals = value;
        }

        private void setMissionCarryInCount(int value) {
            missionCarryInCount = value;
        }

        private void setAppealCarryInCount(int value) {
            appealCarryInCount = value;
        }

        private MonthlyUsageSupplementMetrics toPartialUsageMetrics() {
            MonthlyUsagePeakCandidate peakCandidate = MonthlyUsagePeakCandidate.empty();
            for (Map.Entry<Integer, Long> entry : partialUsageByHour.entrySet()) {
                MonthlyUsagePeakCandidate candidate =
                        new MonthlyUsagePeakCandidate(
                                entry.getKey(), entry.getKey() + 1, entry.getValue());
                if (candidate.isBetterThan(peakCandidate)) {
                    peakCandidate = candidate;
                }
            }
            return new MonthlyUsageSupplementMetrics(
                    partialTotalUsedBytes,
                    new LinkedHashMap<>(partialUsageByWeekday),
                    peakCandidate);
        }

        private MonthlyMissionSummary toMissionSummary() {
            int totalMissionCount = partialMissionCreatedCount;
            int completedMissionCount = partialMissionCompletedCount;
            int rejectedRequestCount = partialMissionRejectedCount;
            for (MonthlyWeeklyRecapSnapshot snapshot : fullWeekSnapshots) {
                totalMissionCount += snapshot.missionCreatedCount();
                completedMissionCount += snapshot.missionCompletedCount();
                rejectedRequestCount += snapshot.missionRejectedCount();
            }
            return new MonthlyMissionSummary(
                    totalMissionCount, completedMissionCount, rejectedRequestCount);
        }

        private MonthlyAppealSummary toAppealSummary() {
            int totalAppeals = partialTotalAppeals;
            int approvedAppeals = partialApprovedAppeals;
            int rejectedAppeals = partialRejectedAppeals;
            for (MonthlyWeeklyRecapSnapshot snapshot : fullWeekSnapshots) {
                totalAppeals += snapshot.totalAppealCount();
                approvedAppeals += snapshot.approvedAppealCount();
                rejectedAppeals += snapshot.rejectedAppealCount();
            }
            return new MonthlyAppealSummary(totalAppeals, approvedAppeals, rejectedAppeals);
        }

        private MonthlyAppealHighlights toAppealHighlights() {
            return new MonthlyAppealHighlights(
                    buildTopSuccessfulRequester(), buildTopAcceptedApprover());
        }

        private MonthlyAppealHighlights.TopSuccessfulRequester buildTopSuccessfulRequester() {
            Map<Long, AppealActorSummary> summaries = new HashMap<>();
            for (ApprovedAppealEvent event : approvedAppealEvents) {
                if (event.requesterId() == null) {
                    continue;
                }
                summaries
                        .computeIfAbsent(
                                event.requesterId(),
                                requesterId ->
                                        new AppealActorSummary(
                                                requesterId,
                                                event.requesterName(),
                                                new ArrayList<>()))
                        .add(event);
            }

            return summaries.values().stream()
                    .sorted(AppealActorSummary.TOP_ACTOR_COMPARATOR)
                    .findFirst()
                    .map(
                            summary ->
                                    new MonthlyAppealHighlights.TopSuccessfulRequester(
                                            summary.actorId,
                                            summary.actorName,
                                            summary.events.size(),
                                            summary.events.stream()
                                                    .sorted(
                                                            AppealActorSummary
                                                                    .RECENT_APPROVED_COMPARATOR)
                                                    .limit(3)
                                                    .map(
                                                            event ->
                                                                    new MonthlyAppealHighlights
                                                                            .RecentApprovedAppeal(
                                                                            event.appealId(),
                                                                            event.approverId(),
                                                                            event.approverName(),
                                                                            event.requestReason(),
                                                                            event.requestedAt()))
                                                    .toList()))
                    .orElseGet(MonthlyAppealHighlights.TopSuccessfulRequester::empty);
        }

        private MonthlyAppealHighlights.TopAcceptedApprover buildTopAcceptedApprover() {
            Map<Long, AppealActorSummary> summaries = new HashMap<>();
            for (ApprovedAppealEvent event : approvedAppealEvents) {
                if (event.approverId() == null) {
                    continue;
                }
                summaries
                        .computeIfAbsent(
                                event.approverId(),
                                approverId ->
                                        new AppealActorSummary(
                                                approverId,
                                                event.approverName(),
                                                new ArrayList<>()))
                        .add(event);
            }

            return summaries.values().stream()
                    .sorted(AppealActorSummary.TOP_ACTOR_COMPARATOR)
                    .findFirst()
                    .map(
                            summary ->
                                    new MonthlyAppealHighlights.TopAcceptedApprover(
                                            summary.actorId,
                                            summary.actorName,
                                            summary.events.size(),
                                            summary.events.stream()
                                                    .sorted(
                                                            AppealActorSummary
                                                                    .RECENT_ACCEPTED_COMPARATOR)
                                                    .limit(3)
                                                    .map(
                                                            event ->
                                                                    new MonthlyAppealHighlights
                                                                            .RecentAcceptedAppeal(
                                                                            event.appealId(),
                                                                            event.requesterId(),
                                                                            event.requesterName(),
                                                                            event.requestReason(),
                                                                            event.resolvedAt()
                                                                                    .toLocalDateTime()
                                                                                    .format(
                                                                                            ISO_DATE_TIME)))
                                                    .toList()))
                    .orElseGet(MonthlyAppealHighlights.TopAcceptedApprover::empty);
        }

        private static Map<String, Long> initWeekdayUsageMap() {
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
    }

    private static final class AppealActorSummary {

        private static final Comparator<AppealActorSummary> TOP_ACTOR_COMPARATOR =
                Comparator.comparingInt(AppealActorSummary::count)
                        .reversed()
                        .thenComparing(
                                AppealActorSummary::latestResolvedAt, Comparator.reverseOrder())
                        .thenComparing(
                                AppealActorSummary::actorId, Comparator.nullsLast(Long::compareTo));

        private static final Comparator<ApprovedAppealEvent> RECENT_APPROVED_COMPARATOR =
                Comparator.comparing(
                                ApprovedAppealEvent::resolvedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                                ApprovedAppealEvent::appealId,
                                Comparator.nullsLast(Comparator.reverseOrder()));

        private static final Comparator<ApprovedAppealEvent> RECENT_ACCEPTED_COMPARATOR =
                Comparator.comparing(
                                ApprovedAppealEvent::resolvedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                                ApprovedAppealEvent::appealId,
                                Comparator.nullsLast(Comparator.reverseOrder()));

        private final Long actorId;
        private final String actorName;
        private final List<ApprovedAppealEvent> events;

        private AppealActorSummary(
                Long actorId, String actorName, List<ApprovedAppealEvent> events) {
            this.actorId = actorId;
            this.actorName = actorName;
            this.events = events;
        }

        private void add(ApprovedAppealEvent event) {
            events.add(event);
        }

        private int count() {
            return events.size();
        }

        private Timestamp latestResolvedAt() {
            return events.stream()
                    .map(ApprovedAppealEvent::resolvedAt)
                    .max(Timestamp::compareTo)
                    .orElse(Timestamp.valueOf(LocalDateTime.MIN));
        }

        private Long actorId() {
            return actorId;
        }
    }
}
