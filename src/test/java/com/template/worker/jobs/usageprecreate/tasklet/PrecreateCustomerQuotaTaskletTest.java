package com.template.worker.jobs.usageprecreate.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.template.worker.global.retry.BatchRetrySupport;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class PrecreateCustomerQuotaTaskletTest {

    @Mock private MonthlyUsagePrecreateJobParameterSupport parameterSupport;

    private JdbcTemplate jdbcTemplate;
    private PrecreateCustomerQuotaTasklet tasklet;

    @BeforeEach
    void setUp() {
        DataSource dataSource = createDataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        tasklet =
                new PrecreateCustomerQuotaTasklet(
                        new NamedParameterJdbcTemplate(dataSource),
                        parameterSupport,
                        new BatchRetrySupport(3, 0L));

        dropTables();
        createTables();
    }

    @Test
    @DisplayName("execute - 최신 snapshot의 monthly limit만 이어받고 차단 상태는 초기화한다")
    void execute_copiesOnlyMonthlyLimitAndResetsBlockState() {
        jdbcTemplate.update(
                "INSERT INTO family_member (id, family_id, customer_id, deleted_at) VALUES (1, 10,"
                        + " 100, NULL)");
        jdbcTemplate.update(
                "INSERT INTO customer_quota (id, customer_id, family_id, monthly_limit_bytes,"
                    + " monthly_used_bytes, current_month, is_blocked, block_reason, created_at,"
                    + " updated_at, deleted_at) VALUES (1, 100, 10, 5000, 1200, DATE '2026-02-01',"
                    + " TRUE, 'MANUAL_BLOCK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)");

        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        tasklet.beforeStep(stepExecution);
        tasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM customer_quota WHERE customer_id = 100 AND"
                                        + " family_id = 10 AND current_month = DATE '2026-04-01'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT monthly_limit_bytes FROM customer_quota WHERE customer_id ="
                                        + " 100 AND family_id = 10 AND current_month = DATE"
                                        + " '2026-04-01'",
                                Long.class))
                .isEqualTo(5000L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT monthly_used_bytes FROM customer_quota WHERE customer_id ="
                                        + " 100 AND family_id = 10 AND current_month = DATE"
                                        + " '2026-04-01'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT is_blocked FROM customer_quota WHERE customer_id = 100 AND"
                                        + " family_id = 10 AND current_month = DATE '2026-04-01'",
                                Boolean.class))
                .isFalse();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT block_reason FROM customer_quota WHERE customer_id = 100"
                                    + " AND family_id = 10 AND current_month = DATE '2026-04-01'",
                                String.class))
                .isNull();
        assertThat(
                        stepExecution
                                .getJobExecution()
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsagePrecreateJobConstants
                                                .JOB_CONTEXT_PRECREATED_CUSTOMER_QUOTA_COUNT))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("execute - 대상 월 row가 이미 있으면 중복 생성하지 않는다")
    void execute_doesNotCreateDuplicateTargetMonthRow() {
        jdbcTemplate.update(
                "INSERT INTO family_member (id, family_id, customer_id, deleted_at) VALUES (1, 10,"
                        + " 100, NULL)");
        jdbcTemplate.update(
                "INSERT INTO customer_quota (id, customer_id, family_id, monthly_limit_bytes,"
                    + " monthly_used_bytes, current_month, is_blocked, block_reason, created_at,"
                    + " updated_at, deleted_at) VALUES (1, 100, 10, 5000, 1200, DATE '2026-04-01',"
                    + " FALSE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)");

        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        tasklet.beforeStep(stepExecution);
        tasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM customer_quota WHERE customer_id = 100 AND"
                                        + " family_id = 10 AND current_month = DATE '2026-04-01'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        stepExecution
                                .getJobExecution()
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsagePrecreateJobConstants
                                                .JOB_CONTEXT_PRECREATED_CUSTOMER_QUOTA_COUNT))
                .isZero();
    }

    @Test
    @DisplayName("execute - 최신 snapshot이 없어도 기본값으로 생성한다")
    void execute_createsDefaultRowWhenSnapshotDoesNotExist() {
        jdbcTemplate.update(
                "INSERT INTO family_member (id, family_id, customer_id, deleted_at) VALUES (1, 10,"
                        + " 100, NULL)");

        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        tasklet.beforeStep(stepExecution);
        tasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT monthly_limit_bytes FROM customer_quota WHERE customer_id ="
                                        + " 100 AND family_id = 10 AND current_month = DATE"
                                        + " '2026-04-01'",
                                Long.class))
                .isNull();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT monthly_used_bytes FROM customer_quota WHERE customer_id ="
                                        + " 100 AND family_id = 10 AND current_month = DATE"
                                        + " '2026-04-01'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT is_blocked FROM customer_quota WHERE customer_id = 100 AND"
                                        + " family_id = 10 AND current_month = DATE '2026-04-01'",
                                Boolean.class))
                .isFalse();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT block_reason FROM customer_quota WHERE customer_id = 100"
                                    + " AND family_id = 10 AND current_month = DATE '2026-04-01'",
                                String.class))
                .isNull();
    }

    @Test
    @DisplayName("execute - QueryTimeoutException 이 두 번 나도 세 번째에 성공한다")
    void execute_retriesQueryTimeoutExceptionThenSucceeds() {
        NamedParameterJdbcTemplate namedParameterJdbcTemplate =
                mock(NamedParameterJdbcTemplate.class);
        PrecreateCustomerQuotaTasklet retryTasklet =
                new PrecreateCustomerQuotaTasklet(
                        namedParameterJdbcTemplate, parameterSupport, new BatchRetrySupport(3, 0L));
        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new QueryTimeoutException("timeout"))
                .thenThrow(new QueryTimeoutException("timeout"))
                .thenReturn(1);

        retryTasklet.beforeStep(stepExecution);
        retryTasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));

        verify(namedParameterJdbcTemplate, times(3))
                .update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("execute - QueryTimeoutException 이 재시도 한도를 넘으면 예외를 전파한다")
    void execute_throwsWhenQueryTimeoutExceedsRetryLimit() {
        NamedParameterJdbcTemplate namedParameterJdbcTemplate =
                mock(NamedParameterJdbcTemplate.class);
        PrecreateCustomerQuotaTasklet retryTasklet =
                new PrecreateCustomerQuotaTasklet(
                        namedParameterJdbcTemplate, parameterSupport, new BatchRetrySupport(3, 0L));
        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new QueryTimeoutException("timeout"));

        retryTasklet.beforeStep(stepExecution);

        assertThatThrownBy(() -> executeCustomerTasklet(retryTasklet, stepExecution))
                .isInstanceOf(QueryTimeoutException.class);

        verify(namedParameterJdbcTemplate, times(3))
                .update(anyString(), any(MapSqlParameterSource.class));
    }

    private void executeCustomerTasklet(
            PrecreateCustomerQuotaTasklet retryTasklet, StepExecution stepExecution) {
        retryTasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));
    }

    private StepExecution createStepExecution(LocalDate targetMonth) {
        JobParameters jobParameters =
                new JobParametersBuilder()
                        .addString("targetMonth", targetMonth.toString())
                        .toJobParameters();
        JobInstance jobInstance = new JobInstance(1L, "monthly-usage-precreate-job");
        JobExecution jobExecution = new JobExecution(jobInstance, jobParameters);
        StepExecution stepExecution =
                new StepExecution("precreate-customer-quota-step", jobExecution);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        return stepExecution;
    }

    private DataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:precreate_customer_quota_job_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS customer_quota");
        jdbcTemplate.execute("DROP TABLE IF EXISTS family_member");
    }

    private void createTables() {
        jdbcTemplate.execute(
                "CREATE TABLE family_member (id BIGINT PRIMARY KEY, family_id BIGINT NOT NULL,"
                        + " customer_id BIGINT NOT NULL, deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE customer_quota (id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id"
                        + " BIGINT NOT NULL, family_id BIGINT NOT NULL, monthly_limit_bytes BIGINT"
                        + " NULL, monthly_used_bytes BIGINT NOT NULL, current_month DATE NOT NULL,"
                        + " is_blocked BOOLEAN NOT NULL, block_reason VARCHAR(50) NULL, created_at"
                        + " TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP"
                        + " NULL)");
    }
}
