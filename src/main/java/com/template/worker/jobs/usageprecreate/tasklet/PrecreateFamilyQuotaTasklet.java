package com.template.worker.jobs.usageprecreate.tasklet;

import java.sql.Date;
import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.common.retry.BatchRetrySupport;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobParameterSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrecreateFamilyQuotaTasklet implements Tasklet, StepExecutionListener {

    // 최신 스냅샷이 있는 가족에게 대상 월 family_quota를 선생성
    private static final String INSERT_FAMILY_QUOTA_SQL =
            """
            INSERT INTO family_quota (
                family_id,
                current_month,
                total_quota_bytes,
                used_bytes,
                created_at,
                updated_at,
                deleted_at
            )
            SELECT f.id,
                   :targetMonth AS current_month,
                   latest.total_quota_bytes AS total_quota_bytes,
                   0 AS used_bytes,
                   CURRENT_TIMESTAMP AS created_at,
                   CURRENT_TIMESTAMP AS updated_at,
                   NULL AS deleted_at
            FROM family f
            LEFT JOIN family_quota current_row
              ON current_row.family_id = f.id
             AND current_row.current_month = :targetMonth
             AND current_row.deleted_at IS NULL
            JOIN (
                -- 가족별 최신 family_quota 1건만 추림
                SELECT family_id,
                       total_quota_bytes
                FROM (
                    SELECT family_id,
                           total_quota_bytes,
                           ROW_NUMBER() OVER (
                               PARTITION BY family_id
                               ORDER BY current_month DESC, id DESC
                           ) AS rn
                    FROM family_quota
                    WHERE deleted_at IS NULL
                ) ranked_quota
                WHERE ranked_quota.rn = 1
            ) latest
              ON latest.family_id = f.id
            WHERE f.deleted_at IS NULL
              AND current_row.id IS NULL
            """;

    // 최신 family_quota 스냅샷이 없어 선생성을 건너뛸 가족 수를 집계
    private static final String COUNT_SKIPPED_FAMILY_QUOTA_SQL =
            """
            SELECT COUNT(*)
            FROM family f
            WHERE f.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM family_quota current_row
                  WHERE current_row.family_id = f.id
                    AND current_row.current_month = :targetMonth
                    AND current_row.deleted_at IS NULL
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM family_quota snapshot
                  WHERE snapshot.family_id = f.id
                    AND snapshot.deleted_at IS NULL
              )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MonthlyUsagePrecreateJobParameterSupport parameterSupport;
    private final BatchRetrySupport batchRetrySupport;

    private LocalDate targetMonth;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // 모든 선생성 기준 월을 동일하게 맞추기 위해 파라미터를 선해석
        JobParameters jobParameters = stepExecution.getJobParameters();
        targetMonth = parameterSupport.resolveTargetMonth(jobParameters);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("targetMonth", Date.valueOf(targetMonth));

        // 최신 스냅샷이 전혀 없는 가족 수를 먼저 계산해 skip 집계에 사용
        PrecreateFamilyQuotaResult result =
                batchRetrySupport
                        .createDbRetryTemplate()
                        .execute(
                                retryContext -> {
                                    Long skippedCount =
                                            jdbcTemplate.queryForObject(
                                                    COUNT_SKIPPED_FAMILY_QUOTA_SQL,
                                                    params,
                                                    Long.class);
                                    long resolvedSkippedCount =
                                            skippedCount == null ? 0L : skippedCount;
                                    // 최신 스냅샷이 있는 가족만 대상 월 row를 멱등적으로 선생성
                                    int insertedCount =
                                            jdbcTemplate.update(INSERT_FAMILY_QUOTA_SQL, params);
                                    return new PrecreateFamilyQuotaResult(
                                            insertedCount, resolvedSkippedCount);
                                });

        StepExecution stepExecution = contribution.getStepExecution();
        stepExecution
                .getJobExecution()
                .getExecutionContext()
                .putLong(
                        MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_PRECREATED_FAMILY_QUOTA_COUNT,
                        result.insertedCount());
        stepExecution
                .getJobExecution()
                .getExecutionContext()
                .putLong(
                        MonthlyUsagePrecreateJobConstants.JOB_CONTEXT_SKIPPED_FAMILY_QUOTA_COUNT,
                        result.skippedCount());

        if (result.skippedCount() > 0) {
            log.warn(
                    "Skipped family_quota precreate because latest snapshot was not found."
                            + " targetMonth={}, skippedCount={}",
                    targetMonth,
                    result.skippedCount());
        }
        log.info(
                "Precreated family_quota rows for targetMonth. targetMonth={}, insertedCount={}",
                targetMonth,
                result.insertedCount());

        return RepeatStatus.FINISHED;
    }

    private record PrecreateFamilyQuotaResult(int insertedCount, long skippedCount) {}
}
