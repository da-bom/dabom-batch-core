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

import com.template.worker.global.retry.BatchRetrySupport;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobParameterSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrecreateCustomerQuotaTasklet implements Tasklet, StepExecutionListener {

    // 대상 월 customer_quota가 없는 구성원에게 기본 row를 선생성
    private static final String INSERT_CUSTOMER_QUOTA_SQL =
            """
            INSERT INTO customer_quota (
                customer_id,
                family_id,
                monthly_limit_bytes,
                monthly_used_bytes,
                current_month,
                is_blocked,
                block_reason,
                created_at,
                updated_at,
                deleted_at
            )
            SELECT fm.customer_id,
                   fm.family_id,
                   latest.monthly_limit_bytes AS monthly_limit_bytes,
                   0 AS monthly_used_bytes,
                   :targetMonth AS current_month,
                   FALSE AS is_blocked,
                   NULL AS block_reason,
                   CURRENT_TIMESTAMP AS created_at,
                   CURRENT_TIMESTAMP AS updated_at,
                   NULL AS deleted_at
            FROM family_member fm
            LEFT JOIN customer_quota current_row
              ON current_row.customer_id = fm.customer_id
             AND current_row.family_id = fm.family_id
             AND current_row.current_month = :targetMonth
             AND current_row.deleted_at IS NULL
            LEFT JOIN (
                -- 가족/구성원별 최신 customer_quota 1건만 추림
                SELECT customer_id,
                       family_id,
                       monthly_limit_bytes
                FROM (
                    SELECT customer_id,
                           family_id,
                           monthly_limit_bytes,
                           ROW_NUMBER() OVER (
                               PARTITION BY customer_id, family_id
                               ORDER BY current_month DESC, id DESC
                           ) AS rn
                    FROM customer_quota
                    WHERE deleted_at IS NULL
                ) ranked_quota
                WHERE ranked_quota.rn = 1
            ) latest
              ON latest.customer_id = fm.customer_id
             AND latest.family_id = fm.family_id
            WHERE fm.deleted_at IS NULL
              AND current_row.id IS NULL
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

        // 대상 월 row가 없는 구성원만 멱등적으로 선생성
        int insertedCount =
                batchRetrySupport
                        .createDbRetryTemplate()
                        .execute(
                                retryContext ->
                                        jdbcTemplate.update(INSERT_CUSTOMER_QUOTA_SQL, params));

        StepExecution stepExecution = contribution.getStepExecution();
        stepExecution
                .getJobExecution()
                .getExecutionContext()
                .putLong(
                        MonthlyUsagePrecreateJobConstants
                                .JOB_CONTEXT_PRECREATED_CUSTOMER_QUOTA_COUNT,
                        insertedCount);

        log.info(
                "Precreated customer_quota rows for targetMonth. targetMonth={}, insertedCount={}",
                targetMonth,
                insertedCount);

        return RepeatStatus.FINISHED;
    }
}
