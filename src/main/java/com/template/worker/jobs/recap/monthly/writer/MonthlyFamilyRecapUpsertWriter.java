package com.template.worker.jobs.recap.monthly.writer;

import java.sql.Date;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapRow;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyFamilyRecapUpsertWriter implements ItemWriter<MonthlyFamilyRecapRow> {

    // 월간 recap 결과를 family_id와 report_month 기준으로 업서트
    private static final String UPSERT_MONTHLY_RECAP_SQL =
            """
            INSERT INTO family_recap_monthly (
              family_id,
              report_month,
              total_used_bytes,
              total_quota_bytes,
              usage_rate_percent,
              usage_by_weekday,
              peak_usage,
              mission_summary_json,
              appeal_summary_json,
              appeal_highlights_json,
              communication_score,
              created_at,
              updated_at
            )
            VALUES (
              :familyId,
              :reportMonth,
              :totalUsedBytes,
              :totalQuotaBytes,
              :usageRatePercent,
              :usageByWeekday,
              :peakUsage,
              :missionSummary,
              :appealSummary,
              :appealHighlights,
              :communicationScore,
              NOW(),
              NOW()
            )
            ON DUPLICATE KEY UPDATE
              total_used_bytes = VALUES(total_used_bytes),
              total_quota_bytes = VALUES(total_quota_bytes),
              usage_rate_percent = VALUES(usage_rate_percent),
              usage_by_weekday = VALUES(usage_by_weekday),
              peak_usage = VALUES(peak_usage),
              mission_summary_json = VALUES(mission_summary_json),
              appeal_summary_json = VALUES(appeal_summary_json),
              appeal_highlights_json = VALUES(appeal_highlights_json),
              communication_score = VALUES(communication_score),
              updated_at = NOW()
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void write(Chunk<? extends MonthlyFamilyRecapRow> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // 청크 아이템을 배치 파라미터로 변환
        SqlParameterSource[] batchParams =
                chunk.getItems().stream()
                        .map(this::toSqlParameterSource)
                        .toArray(SqlParameterSource[]::new);

        // UNIQUE(family_id, report_month) 기준 멱등 업서트 실행
        jdbcTemplate.batchUpdate(UPSERT_MONTHLY_RECAP_SQL, batchParams);
    }

    private SqlParameterSource toSqlParameterSource(MonthlyFamilyRecapRow row) {
        return new MapSqlParameterSource()
                .addValue("familyId", row.familyId())
                .addValue("reportMonth", Date.valueOf(row.reportMonth()))
                .addValue("totalUsedBytes", row.totalUsedBytes())
                .addValue("totalQuotaBytes", row.totalQuotaBytes())
                .addValue("usageRatePercent", row.usageRatePercent())
                .addValue("usageByWeekday", row.usageByWeekdayJson())
                .addValue("peakUsage", row.peakUsageJson())
                .addValue("missionSummary", row.missionSummaryJson())
                .addValue("appealSummary", row.appealSummaryJson())
                .addValue("appealHighlights", row.appealHighlightsJson())
                .addValue("communicationScore", row.communicationScore());
    }
}
