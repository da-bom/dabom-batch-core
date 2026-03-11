package com.template.worker.jobs.recap.monthly.query;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapSourceMetrics;
import com.template.worker.jobs.recap.monthly.model.MonthlyWeeklyRecapSnapshot;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapAggregationRepository {

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
                   appeal_count
            FROM family_recap_weekly
            WHERE family_id = :familyId
              AND week_start_date >= :monthStartDate
              AND DATE_ADD(week_start_date, INTERVAL 7 DAY) <= :monthEndExclusiveDate
            ORDER BY week_start_date ASC
            """;

    private static final String READ_LATEST_QUOTA_SNAPSHOT_FROM_WEEKLY_SQL =
            """
            SELECT total_quota_bytes
            FROM family_recap_weekly
            WHERE family_id = :familyId
              AND week_start_date < :monthEndExclusiveDate
              AND DATE_ADD(week_start_date, INTERVAL 7 DAY) > :monthStartDate
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

    private static final String READ_TOTAL_NORMAL_APPEALS_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.created_at >= :monthStart
              AND pa.created_at < :monthEndExclusive
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_APPROVED_NORMAL_APPEALS_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'APPROVED'
              AND pa.created_at >= :monthStart
              AND pa.created_at < :monthEndExclusive
              AND pa.resolved_at < :monthEndExclusive
              AND pas.deleted_at IS NULL
            """;

    private static final String READ_REJECTED_NORMAL_APPEALS_SQL =
            """
            SELECT COUNT(*)
            FROM policy_appeal pa
            JOIN policy_assignment pas ON pa.policy_assignment_id = pas.id
            WHERE pas.family_id = :familyId
              AND pa.type = 'NORMAL'
              AND pa.status = 'REJECTED'
              AND pa.created_at >= :monthStart
              AND pa.created_at < :monthEndExclusive
              AND pa.resolved_at < :monthEndExclusive
              AND pas.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MonthlyFamilyRecapSourceMetrics aggregate(Long familyId, LocalDate targetMonth) {
        // 월간 집계 구간 [monthStart, monthEndExclusive) 계산
        LocalDateTime monthStart = targetMonth.atStartOfDay();
        LocalDateTime monthEndExclusive = monthStart.plusMonths(1);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("familyId", familyId)
                        .addValue("monthStart", Timestamp.valueOf(monthStart))
                        .addValue("monthEndExclusive", Timestamp.valueOf(monthEndExclusive))
                        .addValue("monthStartDate", Date.valueOf(targetMonth))
                        .addValue("monthEndExclusiveDate", Date.valueOf(targetMonth.plusMonths(1)));

        // 월 내부에 완전히 포함된 full week만 1차 소스로 사용
        List<MonthlyWeeklyRecapSnapshot> fullWeekSnapshots = readFullWeekSnapshots(params);
        // quota는 주간 합산하지 않고 월 스냅샷 단일값으로 조회
        long totalQuotaBytes = readQuotaSnapshot(params);

        // 월간 이의제기 요약은 NORMAL 타입만 집계
        int totalAppeals = readInt(READ_TOTAL_NORMAL_APPEALS_SQL, params);
        int approvedAppeals = readInt(READ_APPROVED_NORMAL_APPEALS_SQL, params);
        int rejectedAppeals = readInt(READ_REJECTED_NORMAL_APPEALS_SQL, params);

        return new MonthlyFamilyRecapSourceMetrics(
                fullWeekSnapshots, totalQuotaBytes, totalAppeals, approvedAppeals, rejectedAppeals);
    }

    private List<MonthlyWeeklyRecapSnapshot> readFullWeekSnapshots(MapSqlParameterSource params) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(READ_FULL_WEEKLY_RECAP_ROWS_SQL, params);

        return rows.stream()
                .map(
                        row ->
                                new MonthlyWeeklyRecapSnapshot(
                                        toLocalDate(row.get("week_start_date")),
                                        ((Number) row.get("total_used_bytes")).longValue(),
                                        ((Number) row.get("total_quota_bytes")).longValue(),
                                        toJsonString(row.get("usage_by_weekday")),
                                        toJsonString(row.get("peak_usage")),
                                        ((Number) row.get("mission_created_count")).intValue(),
                                        ((Number) row.get("mission_completed_count")).intValue(),
                                        ((Number) row.get("mission_rejected_count")).intValue(),
                                        ((Number) row.get("appeal_count")).intValue()))
                .toList();
    }

    private long readQuotaSnapshot(MapSqlParameterSource params) {
        // 주간 스냅샷이 있으면 가장 최근 값을 우선 사용
        Long quotaFromWeekly = readNullableLong(READ_LATEST_QUOTA_SNAPSHOT_FROM_WEEKLY_SQL, params);
        if (quotaFromWeekly != null) {
            return quotaFromWeekly;
        }

        // 주간 스냅샷이 없으면 family 현재 quota로 fallback
        Long quotaFromFamily = readNullableLong(READ_FAMILY_QUOTA_BYTES_SQL, params);
        return quotaFromFamily == null ? 0L : quotaFromFamily;
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
        return value == null ? "{}" : String.valueOf(value);
    }
}
