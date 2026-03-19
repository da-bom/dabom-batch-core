package com.template.worker.jobs.recap.weekly.query;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.weekly.model.WeeklyPeakUsage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapAggregationRepository {

    private static final String READ_TOTAL_USED_BYTES_SQL =
            """
            SELECT family_id, COALESCE(SUM(bytes_used), 0) AS total_used_bytes
            FROM usage_record
            WHERE family_id IN (:familyIds)
              AND event_time >= :weekStart
              AND event_time < :weekEndExclusive
              AND deleted_at IS NULL
            GROUP BY family_id
            """;

    private static final String READ_USAGE_BY_WEEKDAY_SQL =
            """
            SELECT family_id,
                   DAYOFWEEK(event_time) AS day_of_week,
                   COALESCE(SUM(bytes_used), 0) AS total_bytes
            FROM usage_record
            WHERE family_id IN (:familyIds)
              AND event_time >= :weekStart
              AND event_time < :weekEndExclusive
              AND deleted_at IS NULL
            GROUP BY family_id, DAYOFWEEK(event_time)
            """;

    private static final String READ_USAGE_BY_HOUR_SQL =
            """
            SELECT family_id,
                   HOUR(event_time) AS start_hour,
                   COALESCE(SUM(bytes_used), 0) AS peak_bytes
            FROM usage_record
            WHERE family_id IN (:familyIds)
              AND event_time >= :weekStart
              AND event_time < :weekEndExclusive
              AND deleted_at IS NULL
            GROUP BY family_id, HOUR(event_time)
            """;

    private static final String READ_TOTAL_QUOTA_BYTES_SQL =
            """
            SELECT family_id, total_quota_bytes
            FROM family_quota
            WHERE family_id IN (:familyIds)
              AND current_month = :quotaMonth
              AND deleted_at IS NULL
            """;

    private static final String READ_MISSION_CREATED_COUNT_SQL =
            """
            SELECT family_id, COUNT(*) AS mission_created_count
            FROM mission_item
            WHERE family_id IN (:familyIds)
              AND created_at >= :weekStart
              AND created_at < :weekEndExclusive
              AND deleted_at IS NULL
            GROUP BY family_id
            """;

    private static final String READ_MISSION_COMPLETED_COUNT_SQL =
            """
            SELECT family_id, COUNT(*) AS mission_completed_count
            FROM mission_item
            WHERE family_id IN (:familyIds)
              AND status = 'COMPLETED'
              AND completed_at >= :weekStart
              AND completed_at < :weekEndExclusive
              AND deleted_at IS NULL
            GROUP BY family_id
            """;

    private static final String READ_MISSION_REJECTED_COUNT_SQL =
            """
            SELECT mi.family_id, COUNT(*) AS mission_rejected_count
            FROM mission_request mr
            JOIN mission_item mi ON mr.mission_item_id = mi.id
            WHERE mi.family_id IN (:familyIds)
              AND mr.status = 'REJECTED'
              AND mr.resolved_at >= :weekStart
              AND mr.resolved_at < :weekEndExclusive
              AND mr.deleted_at IS NULL
              AND mi.deleted_at IS NULL
            GROUP BY mi.family_id
            """;

    private static final String READ_TOTAL_APPEAL_COUNT_SQL =
            """
            SELECT pas.family_id, COUNT(*) AS total_appeal_count
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND pa.created_at >= :weekStart
              AND pa.created_at < :weekEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            GROUP BY pas.family_id
            """;

    private static final String READ_APPROVED_APPEAL_COUNT_SQL =
            """
            SELECT pas.family_id, COUNT(*) AS approved_appeal_count
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_at >= :weekStart
              AND pa.resolved_at < :weekEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            GROUP BY pas.family_id
            """;

    private static final String READ_REJECTED_APPEAL_COUNT_SQL =
            """
            SELECT pas.family_id, COUNT(*) AS rejected_appeal_count
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id IN (:familyIds)
              AND pa.type = 'NORMAL'
              AND pa.status = 'REJECTED'
              AND pa.resolved_at >= :weekStart
              AND pa.resolved_at < :weekEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            GROUP BY pas.family_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WeeklyFamilyRecapSourceMetrics aggregate(Long familyId, LocalDate weekStartDate) {
        return aggregate(List.of(familyId), weekStartDate).get(familyId);
    }

    public Map<Long, WeeklyFamilyRecapSourceMetrics> aggregate(
            List<Long> familyIds, LocalDate weekStartDate) {
        if (familyIds.isEmpty()) {
            return Map.of();
        }

        LocalDateTime weekStart = weekStartDate.atStartOfDay();
        LocalDateTime weekEndExclusive = weekStart.plusDays(7);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("familyIds", familyIds)
                        .addValue("quotaMonth", Date.valueOf(weekStartDate.withDayOfMonth(1)))
                        .addValue("weekStart", Timestamp.valueOf(weekStart))
                        .addValue("weekEndExclusive", Timestamp.valueOf(weekEndExclusive));

        Map<Long, MutableWeeklyMetrics> metricsByFamily = initMetrics(familyIds);

        applyTotalUsedBytes(params, metricsByFamily);
        applyUsageByWeekday(params, metricsByFamily);
        applyPeakUsage(params, metricsByFamily);
        applyQuota(params, metricsByFamily);
        applyCount(
                READ_MISSION_CREATED_COUNT_SQL,
                "mission_created_count",
                params,
                metricsByFamily,
                MutableWeeklyMetrics::setMissionCreatedCount);
        applyCount(
                READ_MISSION_COMPLETED_COUNT_SQL,
                "mission_completed_count",
                params,
                metricsByFamily,
                MutableWeeklyMetrics::setMissionCompletedCount);
        applyCount(
                READ_MISSION_REJECTED_COUNT_SQL,
                "mission_rejected_count",
                params,
                metricsByFamily,
                MutableWeeklyMetrics::setMissionRejectedCount);
        applyCount(
                READ_TOTAL_APPEAL_COUNT_SQL,
                "total_appeal_count",
                params,
                metricsByFamily,
                MutableWeeklyMetrics::setTotalAppealCount);
        applyCount(
                READ_APPROVED_APPEAL_COUNT_SQL,
                "approved_appeal_count",
                params,
                metricsByFamily,
                MutableWeeklyMetrics::setApprovedAppealCount);
        applyCount(
                READ_REJECTED_APPEAL_COUNT_SQL,
                "rejected_appeal_count",
                params,
                metricsByFamily,
                MutableWeeklyMetrics::setRejectedAppealCount);

        Map<Long, WeeklyFamilyRecapSourceMetrics> result = new LinkedHashMap<>();
        for (Long familyId : familyIds) {
            MutableWeeklyMetrics metrics = metricsByFamily.get(familyId);
            result.put(
                    familyId,
                    new WeeklyFamilyRecapSourceMetrics(
                            metrics.totalUsedBytes,
                            metrics.totalQuotaBytes,
                            metrics.usageBytesByWeekday,
                            metrics.peakUsage,
                            metrics.missionCreatedCount,
                            metrics.missionCompletedCount,
                            metrics.missionRejectedCount,
                            metrics.totalAppealCount,
                            metrics.approvedAppealCount,
                            metrics.rejectedAppealCount));
        }
        return result;
    }

    private Map<Long, MutableWeeklyMetrics> initMetrics(List<Long> familyIds) {
        Map<Long, MutableWeeklyMetrics> metricsByFamily = new LinkedHashMap<>();
        for (Long familyId : familyIds) {
            metricsByFamily.put(familyId, new MutableWeeklyMetrics());
        }
        return metricsByFamily;
    }

    private void applyTotalUsedBytes(
            MapSqlParameterSource params, Map<Long, MutableWeeklyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_TOTAL_USED_BYTES_SQL, params)
                .forEach(
                        row ->
                                metricsByFamily
                                        .get(toLong(row.get("family_id")))
                                        .setTotalUsedBytes(toLong(row.get("total_used_bytes"))));
    }

    private void applyUsageByWeekday(
            MapSqlParameterSource params, Map<Long, MutableWeeklyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_USAGE_BY_WEEKDAY_SQL, params)
                .forEach(
                        row -> {
                            MutableWeeklyMetrics metrics =
                                    metricsByFamily.get(toLong(row.get("family_id")));
                            metrics.usageBytesByWeekday.put(
                                    resolveWeekdayKey(toInt(row.get("day_of_week"))),
                                    toLong(row.get("total_bytes")));
                        });
    }

    private void applyPeakUsage(
            MapSqlParameterSource params, Map<Long, MutableWeeklyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_USAGE_BY_HOUR_SQL, params)
                .forEach(
                        row -> {
                            MutableWeeklyMetrics metrics =
                                    metricsByFamily.get(toLong(row.get("family_id")));
                            int startHour = toInt(row.get("start_hour"));
                            long peakBytes = toLong(row.get("peak_bytes"));
                            if (peakBytes > metrics.peakUsage.peakBytes()
                                    || (peakBytes == metrics.peakUsage.peakBytes()
                                            && startHour < metrics.peakUsage.startHour())) {
                                metrics.peakUsage =
                                        new WeeklyPeakUsage(startHour, startHour + 1, peakBytes);
                            }
                        });
    }

    private void applyQuota(
            MapSqlParameterSource params, Map<Long, MutableWeeklyMetrics> metricsByFamily) {
        jdbcTemplate
                .queryForList(READ_TOTAL_QUOTA_BYTES_SQL, params)
                .forEach(
                        row ->
                                metricsByFamily
                                        .get(toLong(row.get("family_id")))
                                        .setTotalQuotaBytes(toLong(row.get("total_quota_bytes"))));
    }

    private void applyCount(
            String sql,
            String countColumn,
            MapSqlParameterSource params,
            Map<Long, MutableWeeklyMetrics> metricsByFamily,
            CountApplier countApplier) {
        jdbcTemplate
                .queryForList(sql, params)
                .forEach(
                        row ->
                                countApplier.apply(
                                        metricsByFamily.get(toLong(row.get("family_id"))),
                                        toInt(row.get(countColumn))));
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

    private int toInt(Object value) {
        return value instanceof Number numberValue ? numberValue.intValue() : 0;
    }

    private long toLong(Object value) {
        return value instanceof Number numberValue ? numberValue.longValue() : 0L;
    }

    @FunctionalInterface
    private interface CountApplier {
        void apply(MutableWeeklyMetrics metrics, int value);
    }

    private static final class MutableWeeklyMetrics {

        private final Map<String, Long> usageBytesByWeekday = initWeekdayUsageMap();

        private long totalUsedBytes;
        private long totalQuotaBytes;
        private WeeklyPeakUsage peakUsage = new WeeklyPeakUsage(0, 1, 0L);
        private int missionCreatedCount;
        private int missionCompletedCount;
        private int missionRejectedCount;
        private int totalAppealCount;
        private int approvedAppealCount;
        private int rejectedAppealCount;

        private void setTotalUsedBytes(long totalUsedBytes) {
            this.totalUsedBytes = totalUsedBytes;
        }

        private void setTotalQuotaBytes(long totalQuotaBytes) {
            this.totalQuotaBytes = totalQuotaBytes;
        }

        private void setMissionCreatedCount(int missionCreatedCount) {
            this.missionCreatedCount = missionCreatedCount;
        }

        private void setMissionCompletedCount(int missionCompletedCount) {
            this.missionCompletedCount = missionCompletedCount;
        }

        private void setMissionRejectedCount(int missionRejectedCount) {
            this.missionRejectedCount = missionRejectedCount;
        }

        private void setTotalAppealCount(int totalAppealCount) {
            this.totalAppealCount = totalAppealCount;
        }

        private void setApprovedAppealCount(int approvedAppealCount) {
            this.approvedAppealCount = approvedAppealCount;
        }

        private void setRejectedAppealCount(int rejectedAppealCount) {
            this.rejectedAppealCount = rejectedAppealCount;
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
}
