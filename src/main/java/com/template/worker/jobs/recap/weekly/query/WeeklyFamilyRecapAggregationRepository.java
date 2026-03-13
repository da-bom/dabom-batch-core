package com.template.worker.jobs.recap.weekly.query;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
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
            SELECT COALESCE(SUM(bytes_used), 0)
            FROM usage_record
            WHERE family_id = :familyId
              AND event_time >= :weekStart
              AND event_time < :weekEndExclusive
              AND deleted_at IS NULL
            """;

    private static final String READ_USAGE_BY_WEEKDAY_SQL =
            """
            SELECT DAYOFWEEK(event_time) AS day_of_week,
                   COALESCE(SUM(bytes_used), 0) AS total_bytes
            FROM usage_record
            WHERE family_id = :familyId
              AND event_time >= :weekStart
              AND event_time < :weekEndExclusive
              AND deleted_at IS NULL
            GROUP BY DAYOFWEEK(event_time)
            """;

    private static final String READ_PEAK_USAGE_SQL =
            """
            SELECT HOUR(event_time) AS start_hour,
                   COALESCE(SUM(bytes_used), 0) AS peak_bytes
            FROM usage_record
            WHERE family_id = :familyId
              AND event_time >= :weekStart
              AND event_time < :weekEndExclusive
              AND deleted_at IS NULL
            GROUP BY HOUR(event_time)
            ORDER BY peak_bytes DESC, start_hour ASC
            LIMIT 1
            """;

    private static final String READ_TOTAL_QUOTA_BYTES_SQL =
            """
            SELECT total_quota_bytes
            FROM family
            WHERE id = :familyId
              AND deleted_at IS NULL
            """;

    private static final String READ_MISSION_CREATED_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM mission_item
            WHERE family_id = :familyId
              AND created_at >= :weekStart
              AND created_at < :weekEndExclusive
              AND deleted_at IS NULL
            """;

    private static final String READ_MISSION_COMPLETED_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM mission_item
            WHERE family_id = :familyId
              AND status = 'COMPLETED'
              AND completed_at >= :weekStart
              AND completed_at < :weekEndExclusive
              AND deleted_at IS NULL
            """;

    private static final String READ_MISSION_REJECTED_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM mission_request mr
            JOIN mission_item mi ON mr.mission_item_id = mi.id
            WHERE mi.family_id = :familyId
              AND mr.status = 'REJECTED'
              AND mr.resolved_at >= :weekStart
              AND mr.resolved_at < :weekEndExclusive
              AND mr.deleted_at IS NULL
              AND mi.deleted_at IS NULL
            """;

    private static final String READ_TOTAL_APPEAL_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.created_at >= :weekStart
              AND pa.created_at < :weekEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_APPROVED_APPEAL_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.resolved_at >= :weekStart
              AND pa.resolved_at < :weekEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_REJECTED_APPEAL_COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'REJECTED'
              AND pa.resolved_at >= :weekStart
              AND pa.resolved_at < :weekEndExclusive
              AND pa.deleted_at IS NULL
              AND pas.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WeeklyFamilyRecapSourceMetrics aggregate(Long familyId, LocalDate weekStartDate) {
        // 주간 집계 구간 고정
        LocalDateTime weekStart = weekStartDate.atStartOfDay();
        LocalDateTime weekEndExclusive = weekStart.plusDays(7);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("familyId", familyId)
                        .addValue("weekStart", Timestamp.valueOf(weekStart))
                        .addValue("weekEndExclusive", Timestamp.valueOf(weekEndExclusive));

        long totalUsedBytes = readLong(READ_TOTAL_USED_BYTES_SQL, params);
        long totalQuotaBytes = readLong(READ_TOTAL_QUOTA_BYTES_SQL, params);
        Map<String, Long> usageBytesByWeekday = readUsageByWeekday(params);
        WeeklyPeakUsage peakUsage = readPeakUsage(params);

        int missionCreatedCount = readInt(READ_MISSION_CREATED_COUNT_SQL, params);
        int missionCompletedCount = readInt(READ_MISSION_COMPLETED_COUNT_SQL, params);
        int missionRejectedCount = readInt(READ_MISSION_REJECTED_COUNT_SQL, params);
        int totalAppealCount = readInt(READ_TOTAL_APPEAL_COUNT_SQL, params);
        int approvedAppealCount = readInt(READ_APPROVED_APPEAL_COUNT_SQL, params);
        int rejectedAppealCount = readInt(READ_REJECTED_APPEAL_COUNT_SQL, params);

        return new WeeklyFamilyRecapSourceMetrics(
                totalUsedBytes,
                totalQuotaBytes,
                usageBytesByWeekday,
                peakUsage,
                missionCreatedCount,
                missionCompletedCount,
                missionRejectedCount,
                totalAppealCount,
                approvedAppealCount,
                rejectedAppealCount);
    }

    private long readLong(String sql, MapSqlParameterSource params) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
            return value == null ? 0L : value;
        } catch (EmptyResultDataAccessException e) {
            return 0L;
        }
    }

    private int readInt(String sql, MapSqlParameterSource params) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, params, Integer.class);
            return value == null ? 0 : value;
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    private Map<String, Long> readUsageByWeekday(MapSqlParameterSource params) {
        // 7요일 기본값 유지
        Map<String, Long> usageByWeekday = new LinkedHashMap<>();
        usageByWeekday.put("monday", 0L);
        usageByWeekday.put("tuesday", 0L);
        usageByWeekday.put("wednesday", 0L);
        usageByWeekday.put("thursday", 0L);
        usageByWeekday.put("friday", 0L);
        usageByWeekday.put("saturday", 0L);
        usageByWeekday.put("sunday", 0L);

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(READ_USAGE_BY_WEEKDAY_SQL, params);
        for (Map<String, Object> row : rows) {
            int dayOfWeek = ((Number) row.get("day_of_week")).intValue();
            long totalBytes = ((Number) row.get("total_bytes")).longValue();
            usageByWeekday.put(resolveWeekdayKey(dayOfWeek), totalBytes);
        }

        return usageByWeekday;
    }

    private WeeklyPeakUsage readPeakUsage(MapSqlParameterSource params) {
        // 최대 시간대 선택
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(READ_PEAK_USAGE_SQL, params);
        if (rows.isEmpty()) {
            return new WeeklyPeakUsage(0, 1, 0L);
        }

        Map<String, Object> first = rows.get(0);
        int startHour = ((Number) first.get("start_hour")).intValue();
        long peakBytes = ((Number) first.get("peak_bytes")).longValue();
        int endHour = startHour + 1;
        return new WeeklyPeakUsage(startHour, endHour, peakBytes);
    }

    private String resolveWeekdayKey(int dayOfWeek) {
        // MySQL 요일 변환
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
}
