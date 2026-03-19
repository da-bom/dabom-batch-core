package com.template.worker.jobs.recap.weekly.writer;

import java.sql.Date;
import java.util.List;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapRow;
import com.template.worker.jobs.recap.weekly.processor.WeeklyFamilyRecapProcessor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeeklyFamilyRecapUpsertWriter implements ItemWriter<Long>, StepExecutionListener {

    // 주간 recap 결과를 family_id와 week_start_date 기준으로 업서트
    private static final String UPSERT_WEEKLY_RECAP_SQL =
            """
            INSERT INTO family_recap_weekly (
              family_id,
              week_start_date,
              total_used_bytes,
              total_quota_bytes,
              usage_rate_percent,
              usage_by_weekday,
              peak_usage,
              mission_created_count,
              mission_completed_count,
              mission_rejected_count,
              total_appeal_count,
              approved_appeal_count,
              rejected_appeal_count,
              created_at,
              updated_at
            )
            VALUES (
              :familyId,
              :weekStartDate,
              :totalUsedBytes,
              :totalQuotaBytes,
              :usageRatePercent,
              :usageByWeekday,
              :peakUsage,
              :missionCreatedCount,
              :missionCompletedCount,
              :missionRejectedCount,
              :totalAppealCount,
              :approvedAppealCount,
              :rejectedAppealCount,
              NOW(),
              NOW()
            )
            ON DUPLICATE KEY UPDATE
              total_used_bytes = VALUES(total_used_bytes),
              total_quota_bytes = VALUES(total_quota_bytes),
              usage_rate_percent = VALUES(usage_rate_percent),
              usage_by_weekday = VALUES(usage_by_weekday),
              peak_usage = VALUES(peak_usage),
              mission_created_count = VALUES(mission_created_count),
              mission_completed_count = VALUES(mission_completed_count),
              mission_rejected_count = VALUES(mission_rejected_count),
              total_appeal_count = VALUES(total_appeal_count),
              approved_appeal_count = VALUES(approved_appeal_count),
              rejected_appeal_count = VALUES(rejected_appeal_count),
              updated_at = NOW()
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final WeeklyFamilyRecapProcessor processor;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        processor.beforeStep(stepExecution);
    }

    @Override
    public void write(Chunk<? extends Long> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        List<WeeklyFamilyRecapRow> rows = processor.processAll(List.copyOf(chunk.getItems()));

        // 청크 아이템을 배치 파라미터로 변환
        SqlParameterSource[] batchParams =
                rows.stream().map(this::toSqlParameterSource).toArray(SqlParameterSource[]::new);

        // UNIQUE(family_id, week_start_date) 기준 멱등 업서트 실행
        jdbcTemplate.batchUpdate(UPSERT_WEEKLY_RECAP_SQL, batchParams);
    }

    private SqlParameterSource toSqlParameterSource(WeeklyFamilyRecapRow row) {
        return new MapSqlParameterSource()
                .addValue("familyId", row.familyId())
                .addValue("weekStartDate", Date.valueOf(row.weekStartDate()))
                .addValue("totalUsedBytes", row.totalUsedBytes())
                .addValue("totalQuotaBytes", row.totalQuotaBytes())
                .addValue("usageRatePercent", row.usageRatePercent())
                .addValue("usageByWeekday", row.usageByWeekdayJson())
                .addValue("peakUsage", row.peakUsageJson())
                .addValue("missionCreatedCount", row.missionCreatedCount())
                .addValue("missionCompletedCount", row.missionCompletedCount())
                .addValue("missionRejectedCount", row.missionRejectedCount())
                .addValue("totalAppealCount", row.totalAppealCount())
                .addValue("approvedAppealCount", row.approvedAppealCount())
                .addValue("rejectedAppealCount", row.rejectedAppealCount());
    }
}
