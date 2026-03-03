package com.template.worker.jobs.usagereset.tasklet;

import java.sql.Date;
import java.time.LocalDate;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FamilyMonthResetTasklet implements Tasklet, StepExecutionListener {

    private static final String RESET_FAMILY_MONTH_SQL =
            """
            UPDATE family
            SET current_month = :targetMonth,
                used_bytes = 0,
                updated_at = NOW()
            WHERE deleted_at IS NULL
              AND current_month < :targetMonth
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MonthlyUsageResetJobParameterSupport parameterSupport;

    private LocalDate targetMonth;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // 모든 처리 기준 월을 동일하게 맞추기 위해 파라미터를 선해석함
        targetMonth = parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // current_month가 대상 월보다 과거인 row만 갱신해 멱등성을 보장함
        int updatedCount =
                jdbcTemplate.update(
                        RESET_FAMILY_MONTH_SQL,
                        new MapSqlParameterSource("targetMonth", Date.valueOf(targetMonth)));

        // 집계 로그 출력을 위해 갱신 건수를 JobExecutionContext에 기록함
        ExecutionContext jobContext =
                chunkContext
                        .getStepContext()
                        .getStepExecution()
                        .getJobExecution()
                        .getExecutionContext();
        jobContext.putLong(
                MonthlyUsageResetJobConstants.JOB_CONTEXT_DB_UPDATED_FAMILY_COUNT, updatedCount);

        log.info(
                "Reset family current_month and used_bytes. targetMonth={}, updatedCount={}",
                targetMonth,
                updatedCount);
        return RepeatStatus.FINISHED;
    }
}
