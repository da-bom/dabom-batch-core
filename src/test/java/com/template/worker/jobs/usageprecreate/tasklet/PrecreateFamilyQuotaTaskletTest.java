package com.template.worker.jobs.usageprecreate.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobConstants;
import com.template.worker.jobs.usageprecreate.support.MonthlyUsagePrecreateJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class PrecreateFamilyQuotaTaskletTest {

    @Mock private MonthlyUsagePrecreateJobParameterSupport parameterSupport;

    private JdbcTemplate jdbcTemplate;
    private PrecreateFamilyQuotaTasklet tasklet;

    @BeforeEach
    void setUp() {
        DataSource dataSource = createDataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        tasklet =
                new PrecreateFamilyQuotaTasklet(
                        new NamedParameterJdbcTemplate(dataSource), parameterSupport);

        dropTables();
        createTables();
    }

    @Test
    @DisplayName("execute - 최신 snapshot의 total quota를 이어받고 used bytes는 0으로 생성한다")
    void execute_copiesLatestQuotaSnapshot() throws Exception {
        jdbcTemplate.update("INSERT INTO family (id, deleted_at) VALUES (10, NULL)");
        jdbcTemplate.update(
                "INSERT INTO family_quota (id, family_id, current_month, total_quota_bytes,"
                        + " used_bytes, created_at, updated_at, deleted_at) VALUES (1, 10, DATE"
                        + " '2026-03-01', 8000, 1200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)");

        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        tasklet.beforeStep(stepExecution);
        tasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM family_quota WHERE family_id = 10 AND"
                                        + " current_month = DATE '2026-04-01'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT total_quota_bytes FROM family_quota WHERE family_id = 10"
                                        + " AND current_month = DATE '2026-04-01'",
                                Long.class))
                .isEqualTo(8000L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT used_bytes FROM family_quota WHERE family_id = 10 AND"
                                        + " current_month = DATE '2026-04-01'",
                                Long.class))
                .isZero();
        assertThat(
                        stepExecution
                                .getJobExecution()
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsagePrecreateJobConstants
                                                .JOB_CONTEXT_PRECREATED_FAMILY_QUOTA_COUNT))
                .isEqualTo(1L);
        assertThat(
                        stepExecution
                                .getJobExecution()
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsagePrecreateJobConstants
                                                .JOB_CONTEXT_SKIPPED_FAMILY_QUOTA_COUNT))
                .isZero();
    }

    @Test
    @DisplayName("execute - 대상 월 row가 이미 있으면 중복 생성하지 않는다")
    void execute_doesNotCreateDuplicateTargetMonthRow() throws Exception {
        jdbcTemplate.update("INSERT INTO family (id, deleted_at) VALUES (10, NULL)");
        jdbcTemplate.update(
                "INSERT INTO family_quota (id, family_id, current_month, total_quota_bytes,"
                        + " used_bytes, created_at, updated_at, deleted_at) VALUES (1, 10, DATE"
                        + " '2026-04-01', 8000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)");

        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        tasklet.beforeStep(stepExecution);
        tasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM family_quota WHERE family_id = 10 AND"
                                        + " current_month = DATE '2026-04-01'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        stepExecution
                                .getJobExecution()
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsagePrecreateJobConstants
                                                .JOB_CONTEXT_PRECREATED_FAMILY_QUOTA_COUNT))
                .isZero();
    }

    @Test
    @DisplayName("execute - 최신 snapshot이 없으면 건너뛰고 skip count를 남긴다")
    void execute_skipsFamilyWhenSnapshotDoesNotExist() throws Exception {
        jdbcTemplate.update("INSERT INTO family (id, deleted_at) VALUES (10, NULL)");

        StepExecution stepExecution = createStepExecution(LocalDate.of(2026, 4, 1));

        tasklet.beforeStep(stepExecution);
        tasklet.execute(
                new StepContribution(stepExecution),
                new ChunkContext(new StepContext(stepExecution)));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM family_quota WHERE family_id = 10",
                                Integer.class))
                .isZero();
        assertThat(
                        stepExecution
                                .getJobExecution()
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsagePrecreateJobConstants
                                                .JOB_CONTEXT_PRECREATED_FAMILY_QUOTA_COUNT))
                .isZero();
        assertThat(
                        stepExecution
                                .getJobExecution()
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsagePrecreateJobConstants
                                                .JOB_CONTEXT_SKIPPED_FAMILY_QUOTA_COUNT))
                .isEqualTo(1L);
    }

    private StepExecution createStepExecution(LocalDate targetMonth) {
        JobParameters jobParameters =
                new JobParametersBuilder()
                        .addString("targetMonth", targetMonth.toString())
                        .toJobParameters();
        JobInstance jobInstance = new JobInstance(1L, "monthly-usage-precreate-job");
        JobExecution jobExecution = new JobExecution(jobInstance, jobParameters);
        StepExecution stepExecution =
                new StepExecution("precreate-family-quota-step", jobExecution);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        return stepExecution;
    }

    private DataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:precreate_family_quota_job_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS family_quota");
        jdbcTemplate.execute("DROP TABLE IF EXISTS family");
    }

    private void createTables() {
        jdbcTemplate.execute(
                "CREATE TABLE family (id BIGINT PRIMARY KEY, deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE family_quota (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT"
                    + " NOT NULL, current_month DATE NOT NULL, total_quota_bytes BIGINT NOT NULL,"
                    + " used_bytes BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at"
                    + " TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
    }
}
