package com.template.worker.jobs.recap.monthly.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.template.worker.jobs.recap.monthly.model.MonthlyAppealHighlights;
import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapSourceMetrics;

class MonthlyFamilyRecapAggregationRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private MonthlyFamilyRecapAggregationRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = createDataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository =
                new MonthlyFamilyRecapAggregationRepository(
                        new NamedParameterJdbcTemplate(dataSource));

        jdbcTemplate.execute("DROP ALL OBJECTS");
        createTables();
    }

    @Test
    @DisplayName("aggregate - full week snapshot과 partial/raw 집계를 함께 읽는다")
    void aggregate_readsFullWeekAndRawMonthlySupplement() {
        jdbcTemplate.update(
                "INSERT INTO family (id, total_quota_bytes, deleted_at) VALUES (1, 10000, NULL)");

        jdbcTemplate.update(
                "INSERT INTO family_recap_weekly (family_id, week_start_date, total_used_bytes,"
                    + " total_quota_bytes, usage_by_weekday, peak_usage, mission_created_count,"
                    + " mission_completed_count, mission_rejected_count) VALUES (1, DATE"
                    + " '2026-03-02', 1000, 8000,"
                    + " '{\"monday\":100.00,\"tuesday\":0.00,\"wednesday\":0.00,\"thursday\":0.00,\"friday\":0.00,\"saturday\":0.00,\"sunday\":0.00}',"
                    + " '{\"startHour\":21,\"endHour\":22,\"peakBytes\":500}', 2, 1, 1)");
        jdbcTemplate.update(
                "INSERT INTO family_recap_weekly (family_id, week_start_date, total_used_bytes,"
                    + " total_quota_bytes, usage_by_weekday, peak_usage, mission_created_count,"
                    + " mission_completed_count, mission_rejected_count) VALUES (1, DATE"
                    + " '2026-03-23', 2000, 9000,"
                    + " '{\"monday\":0.00,\"tuesday\":100.00,\"wednesday\":0.00,\"thursday\":0.00,\"friday\":0.00,\"saturday\":0.00,\"sunday\":0.00}',"
                    + " '{\"startHour\":20,\"endHour\":21,\"peakBytes\":800}', 1, 1, 0)");

        jdbcTemplate.update(
                "INSERT INTO usage_record (id, family_id, event_time, bytes_used, deleted_at)"
                        + " VALUES (1, 1, TIMESTAMP '2026-03-01 22:10:00', 300, NULL)");
        jdbcTemplate.update(
                "INSERT INTO usage_record (id, family_id, event_time, bytes_used, deleted_at)"
                        + " VALUES (2, 1, TIMESTAMP '2026-03-31 22:20:00', 200, NULL)");
        jdbcTemplate.update(
                "INSERT INTO usage_record (id, family_id, event_time, bytes_used, deleted_at)"
                        + " VALUES (3, 1, TIMESTAMP '2026-03-31 10:00:00', 100, NULL)");
        jdbcTemplate.update(
                "INSERT INTO usage_record (id, family_id, event_time, bytes_used, deleted_at)"
                        + " VALUES (4, 1, TIMESTAMP '2026-03-10 10:00:00', 999, NULL)");

        jdbcTemplate.update(
                "INSERT INTO mission_item (id, family_id, status, completed_at, created_at,"
                    + " deleted_at) VALUES (1, 1, 'ACTIVE', NULL, TIMESTAMP '2026-03-01 08:00:00',"
                    + " NULL)");
        jdbcTemplate.update(
                "INSERT INTO mission_item (id, family_id, status, completed_at, created_at,"
                        + " deleted_at) VALUES (2, 1, 'COMPLETED', TIMESTAMP '2026-03-31 18:00:00',"
                        + " TIMESTAMP '2026-03-15 09:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO mission_request (id, mission_item_id, status, resolved_at, deleted_at)"
                        + " VALUES (1, 2, 'REJECTED', TIMESTAMP '2026-03-31 19:00:00', NULL)");

        jdbcTemplate.update(
                "INSERT INTO customer (id, name, deleted_at) VALUES (101, '김민지', NULL)");
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, deleted_at) VALUES (102, '김민수', NULL)");
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, deleted_at) VALUES (201, '김철수', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_assignment (id, family_id, deleted_at) VALUES (11, 1, NULL)");

        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " request_reason, resolved_by_id, resolved_at, created_at, deleted_at) VALUES"
                    + " (83, 11, 101, 'NORMAL', 'APPROVED', '인강 시청 시간 연장을 요청했어요.', 201, TIMESTAMP"
                    + " '2026-03-12 19:30:00', TIMESTAMP '2026-03-12 19:05:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " request_reason, resolved_by_id, resolved_at, created_at, deleted_at) VALUES"
                    + " (87, 11, 101, 'NORMAL', 'APPROVED', '주말 사용 제한 완화를 요청했어요.', 201, TIMESTAMP"
                    + " '2026-03-18 20:40:00', TIMESTAMP '2026-03-18 20:10:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " request_reason, resolved_by_id, resolved_at, created_at, deleted_at) VALUES"
                    + " (91, 11, 101, 'NORMAL', 'APPROVED', '야간 차단 해제를 요청했어요.', 201, TIMESTAMP"
                    + " '2026-03-21 15:00:00', TIMESTAMP '2026-03-21 14:32:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " request_reason, resolved_by_id, resolved_at, created_at, deleted_at) VALUES"
                    + " (95, 11, 102, 'NORMAL', 'APPROVED', '월말 예외 사용을 요청했어요.', 201, TIMESTAMP"
                    + " '2026-04-02 10:00:00', TIMESTAMP '2026-03-30 09:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " request_reason, resolved_by_id, resolved_at, created_at, deleted_at) VALUES"
                    + " (96, 11, 101, 'NORMAL', 'REJECTED', '게임 시간 연장을 요청했어요.', 201, TIMESTAMP"
                    + " '2026-03-25 11:30:00', TIMESTAMP '2026-03-25 10:00:00', NULL)");
        jdbcTemplate.update(
                "INSERT INTO policy_appeal (id, policy_assignment_id, requester_id, type, status,"
                    + " request_reason, resolved_by_id, resolved_at, created_at, deleted_at) VALUES"
                    + " (97, 11, 101, 'EMERGENCY', 'APPROVED', '긴급 데이터 충전을 요청했어요.', 201, TIMESTAMP"
                    + " '2026-03-10 12:00:00', TIMESTAMP '2026-03-10 11:00:00', NULL)");

        MonthlyFamilyRecapSourceMetrics result = repository.aggregate(1L, LocalDate.of(2026, 3, 1));

        assertThat(result.fullWeekSnapshots()).hasSize(2);
        assertThat(result.totalQuotaBytes()).isEqualTo(9000L);
        assertThat(result.partialUsageMetrics().totalUsedBytes()).isEqualTo(600L);
        assertThat(result.partialUsageMetrics().usageBytesByWeekday())
                .containsEntry("sunday", 300L)
                .containsEntry("tuesday", 300L);
        assertThat(result.partialUsageMetrics().peakUsageCandidate().startHour()).isEqualTo(22);
        assertThat(result.partialUsageMetrics().peakUsageCandidate().peakBytes()).isEqualTo(500L);

        assertThat(result.missionSummary().totalMissionCount()).isEqualTo(2);
        assertThat(result.missionSummary().completedMissionCount()).isEqualTo(1);
        assertThat(result.missionSummary().rejectedRequestCount()).isEqualTo(1);

        assertThat(result.appealSummary().totalAppeals()).isEqualTo(5);
        assertThat(result.appealSummary().approvedAppeals()).isEqualTo(3);
        assertThat(result.appealSummary().rejectedAppeals()).isEqualTo(1);

        MonthlyAppealHighlights.TopSuccessfulRequester topRequester =
                result.appealHighlights().topSuccessfulRequester();
        assertThat(topRequester.requesterId()).isEqualTo(101L);
        assertThat(topRequester.requesterName()).isEqualTo("김민지");
        assertThat(topRequester.approvedAppealCount()).isEqualTo(3);
        assertThat(topRequester.recentApprovedAppeals())
                .extracting(MonthlyAppealHighlights.RecentApprovedAppeal::appealId)
                .containsExactly(91L, 87L, 83L);

        MonthlyAppealHighlights.TopAcceptedApprover topApprover =
                result.appealHighlights().topAcceptedApprover();
        assertThat(topApprover.approverId()).isEqualTo(201L);
        assertThat(topApprover.approvedAppealCount()).isEqualTo(3);
        assertThat(topApprover.recentAcceptedAppeals())
                .extracting(MonthlyAppealHighlights.RecentAcceptedAppeal::appealId)
                .containsExactly(91L, 87L, 83L);
    }

    @Test
    @DisplayName("aggregate - overlapping weekly snapshot이 없으면 family quota와 empty highlight를 사용한다")
    void aggregate_withoutWeeklySnapshot_fallsBackToFamilyQuota() {
        jdbcTemplate.update(
                "INSERT INTO family (id, total_quota_bytes, deleted_at) VALUES (2, 7000, NULL)");

        MonthlyFamilyRecapSourceMetrics result = repository.aggregate(2L, LocalDate.of(2026, 3, 1));

        assertThat(result.fullWeekSnapshots()).isEmpty();
        assertThat(result.totalQuotaBytes()).isEqualTo(7000L);
        assertThat(result.partialUsageMetrics().totalUsedBytes()).isZero();
        assertThat(result.appealHighlights().topSuccessfulRequester().requesterId()).isNull();
        assertThat(result.appealHighlights().topAcceptedApprover().approverId()).isNull();
    }

    private DataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:monthly_recap_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void createTables() {
        jdbcTemplate.execute(
                "CREATE TABLE family (id BIGINT PRIMARY KEY, total_quota_bytes BIGINT NOT NULL,"
                        + " deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE family_recap_weekly ("
                        + "family_id BIGINT NOT NULL, "
                        + "week_start_date DATE NOT NULL, "
                        + "total_used_bytes BIGINT NOT NULL, "
                        + "total_quota_bytes BIGINT NOT NULL, "
                        + "usage_by_weekday VARCHAR(2000), "
                        + "peak_usage VARCHAR(1000), "
                        + "mission_created_count INT NOT NULL, "
                        + "mission_completed_count INT NOT NULL, "
                        + "mission_rejected_count INT NOT NULL)");
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
                "CREATE TABLE customer (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL,"
                        + " deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE policy_assignment (id BIGINT PRIMARY KEY, family_id BIGINT NOT NULL,"
                        + " deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE policy_appeal ("
                        + "id BIGINT PRIMARY KEY, "
                        + "policy_assignment_id BIGINT NULL, "
                        + "requester_id BIGINT NOT NULL, "
                        + "type VARCHAR(20) NOT NULL, "
                        + "status VARCHAR(20) NOT NULL, "
                        + "request_reason VARCHAR(500) NULL, "
                        + "resolved_by_id BIGINT NULL, "
                        + "resolved_at TIMESTAMP NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "deleted_at TIMESTAMP NULL)");
    }
}
