package com.template.worker.jobs.recap.weekly.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapSourceMetrics;

class WeeklyFamilyRecapAggregationRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private WeeklyFamilyRecapAggregationRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = createDataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository =
                new WeeklyFamilyRecapAggregationRepository(
                        new NamedParameterJdbcTemplate(dataSource));

        dropTables();
        createTables();
    }

    @Test
    @DisplayName("aggregate - appeal은 요청 주와 처리 주를 분리해서 집계한다")
    void aggregate_countsAppealRequestedAndResolvedSeparately() {
        jdbcTemplate.update("INSERT INTO family (id, deleted_at) VALUES (1, NULL)");
        jdbcTemplate.update(
                "INSERT INTO family_quota (id, family_id, current_month, total_quota_bytes,"
                    + " used_bytes, deleted_at) VALUES (1, 1, DATE '2026-03-01', 5000, 0, NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_assignment (id, family_id, deleted_at) VALUES (11, 1, NULL)");

        jdbcTemplate.update(
                "INSERT INTO usage_record (id, family_id, event_time, bytes_used, deleted_at)"
                        + " VALUES (1, 1, TIMESTAMP '2026-03-10 10:00:00', 100, NULL)");

        jdbcTemplate.update(
                "INSERT INTO mission_item (id, family_id, status, completed_at, created_at,"
                    + " deleted_at) VALUES (1, 1, 'ACTIVE', NULL, TIMESTAMP '2026-03-10 09:00:00',"
                    + " NULL)");
        jdbcTemplate.update(
                "INSERT INTO mission_item (id, family_id, status, completed_at, created_at,"
                        + " deleted_at) VALUES (2, 1, 'COMPLETED', TIMESTAMP '2026-03-11 18:00:00',"
                        + " TIMESTAMP '2026-03-05 09:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO mission_item (id, family_id, status, completed_at, created_at,"
                    + " deleted_at) VALUES (3, 1, 'ACTIVE', NULL, TIMESTAMP '2026-03-15 08:00:00',"
                    + " NULL)");
        jdbcTemplate.update(
                "INSERT INTO mission_request (id, mission_item_id, status, resolved_at, deleted_at)"
                        + " VALUES (1, 1, 'REJECTED', TIMESTAMP '2026-03-13 12:00:00', NULL)");

        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " resolved_by_id, resolved_at, cancelled_at, created_at, deleted_at) VALUES"
                    + " (1, 11, 101, 'NORMAL', 'APPROVED', 201, TIMESTAMP '2026-03-12 10:00:00',"
                    + " NULL, TIMESTAMP '2026-03-10 09:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " resolved_by_id, resolved_at, cancelled_at, created_at, deleted_at) VALUES"
                    + " (2, 11, 101, 'NORMAL', 'APPROVED', 201, TIMESTAMP '2026-03-10 11:00:00',"
                    + " NULL, TIMESTAMP '2026-03-08 09:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " resolved_by_id, resolved_at, cancelled_at, created_at, deleted_at) VALUES"
                    + " (3, 11, 101, 'NORMAL', 'REJECTED', 201, TIMESTAMP '2026-03-13 15:00:00',"
                    + " NULL, TIMESTAMP '2026-03-11 09:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " resolved_by_id, resolved_at, cancelled_at, created_at, deleted_at) VALUES"
                    + " (4, 11, 101, 'NORMAL', 'PENDING', NULL, NULL, NULL, TIMESTAMP '2026-03-14"
                    + " 09:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " resolved_by_id, resolved_at, cancelled_at, created_at, deleted_at) VALUES"
                    + " (5, 11, 101, 'NORMAL', 'CANCELLED', NULL, NULL, TIMESTAMP '2026-03-11"
                    + " 16:00:00', TIMESTAMP '2026-03-09 10:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " resolved_by_id, resolved_at, cancelled_at, created_at, deleted_at) VALUES"
                    + " (6, 11, 101, 'EMERGENCY', 'APPROVED', 201, TIMESTAMP '2026-03-10 12:00:00',"
                    + " NULL, TIMESTAMP '2026-03-10 11:00:00', NULL)");

        WeeklyFamilyRecapSourceMetrics result = repository.aggregate(1L, LocalDate.of(2026, 3, 9));

        assertThat(result.totalUsedBytes()).isEqualTo(100L);
        assertThat(result.totalQuotaBytes()).isEqualTo(5000L);
        assertThat(result.missionCreatedCount()).isEqualTo(2);
        assertThat(result.missionCompletedCount()).isEqualTo(1);
        assertThat(result.missionRejectedCount()).isEqualTo(1);
        assertThat(result.totalAppealCount()).isEqualTo(4);
        assertThat(result.approvedAppealCount()).isEqualTo(2);
        assertThat(result.rejectedAppealCount()).isEqualTo(1);
    }

    private DataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:weekly_recap_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS policy_appeal");
        jdbcTemplate.execute("DROP TABLE IF EXISTS policy_assignment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS mission_request");
        jdbcTemplate.execute("DROP TABLE IF EXISTS mission_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS usage_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS family_quota");
        jdbcTemplate.execute("DROP TABLE IF EXISTS family");
    }

    private void createTables() {
        jdbcTemplate.execute(
                "CREATE TABLE family (id BIGINT PRIMARY KEY, deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE family_quota (id BIGINT PRIMARY KEY, family_id BIGINT NOT NULL,"
                    + " current_month DATE NOT NULL, total_quota_bytes BIGINT NOT NULL, used_bytes"
                    + " BIGINT NOT NULL, deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE usage_record (id BIGINT PRIMARY KEY, family_id BIGINT NOT NULL,"
                        + " event_time TIMESTAMP NOT NULL, bytes_used BIGINT NOT NULL, deleted_at"
                        + " TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE mission_item (id BIGINT PRIMARY KEY, family_id BIGINT NOT NULL,"
                        + " status VARCHAR(20) NOT NULL, completed_at TIMESTAMP NULL, created_at"
                        + " TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE mission_request (id BIGINT PRIMARY KEY, mission_item_id BIGINT NOT"
                    + " NULL, status VARCHAR(20) NOT NULL, resolved_at TIMESTAMP NULL, deleted_at"
                    + " TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE policy_assignment (id BIGINT PRIMARY KEY, family_id BIGINT NOT NULL,"
                        + " deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE policy_appeal (id BIGINT PRIMARY KEY, policy_assignment_id BIGINT"
                        + " NULL, requester_id BIGINT NOT NULL, type VARCHAR(20) NOT NULL, status"
                        + " VARCHAR(20) NOT NULL, resolved_by_id BIGINT NULL, resolved_at TIMESTAMP"
                        + " NULL, cancelled_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL,"
                        + " deleted_at TIMESTAMP NULL)");
    }
}
