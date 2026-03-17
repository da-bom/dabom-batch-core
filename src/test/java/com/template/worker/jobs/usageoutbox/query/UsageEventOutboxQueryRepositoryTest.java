package com.template.worker.jobs.usageoutbox.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.template.worker.jobs.usageoutbox.model.UsageEventOutboxRow;
import com.template.worker.jobs.usageoutbox.support.UsageEventOutboxStatus;

class UsageEventOutboxQueryRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private UsageEventOutboxQueryRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = createDataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository =
                new UsageEventOutboxQueryRepository(new NamedParameterJdbcTemplate(dataSource));

        jdbcTemplate.execute("DROP TABLE IF EXISTS usage_event_outbox");
        jdbcTemplate.execute(
                """
                CREATE TABLE usage_event_outbox (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  event_id VARCHAR(255) NOT NULL,
                  family_id BIGINT NOT NULL,
                  customer_id BIGINT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  payload_json TEXT,
                  retry_count INT NOT NULL DEFAULT 0,
                  next_retry_at TIMESTAMP NULL,
                  last_error VARCHAR(1000) NULL,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NULL,
                  deleted_at TIMESTAMP NULL
                )
                """);
    }

    @Test
    @DisplayName("pollPublishableRows - PUBLISH_PENDING이면서 재시도 가능 시각인 row만 조회한다")
    void pollPublishableRows_readsPublishableRowsOnly() {
        jdbcTemplate.update(
                """
                INSERT INTO usage_event_outbox (
                  event_id, family_id, customer_id, status, payload_json, retry_count,
                  next_retry_at, created_at, updated_at, deleted_at
                ) VALUES (
                  'evt_pending', 10, 1, 'PUBLISH_PENDING', '{}', 0, NULL,
                  TIMESTAMP '2026-03-16 10:00:00', TIMESTAMP '2026-03-16 10:00:00', NULL
                )
                """);
        jdbcTemplate.update(
                """
                INSERT INTO usage_event_outbox (
                  event_id, family_id, customer_id, status, payload_json, retry_count,
                  next_retry_at, created_at, updated_at, deleted_at
                ) VALUES (
                  'evt_retry_due', 10, 1, 'PUBLISH_PENDING', '{}', 1, TIMESTAMP '2026-03-16 10:00:00',
                  TIMESTAMP '2026-03-16 09:59:00', TIMESTAMP '2026-03-16 10:00:00', NULL
                )
                """);
        jdbcTemplate.update(
                """
                INSERT INTO usage_event_outbox (
                  event_id, family_id, customer_id, status, payload_json, retry_count,
                  next_retry_at, created_at, updated_at, deleted_at
                ) VALUES (
                  'evt_retry_later', 10, 1, 'PUBLISH_PENDING', '{}', 1, TIMESTAMP '2026-03-16 10:10:00',
                  TIMESTAMP '2026-03-16 09:58:00', TIMESTAMP '2026-03-16 10:00:00', NULL
                )
                """);
        jdbcTemplate.update(
                """
                INSERT INTO usage_event_outbox (
                  event_id, family_id, customer_id, status, payload_json, retry_count,
                  next_retry_at, created_at, updated_at, deleted_at
                ) VALUES (
                  'evt_failed', 10, 1, 'FAILED', '{}', 1, NULL,
                  TIMESTAMP '2026-03-16 09:57:00', TIMESTAMP '2026-03-16 10:00:00', NULL
                )
                """);
        jdbcTemplate.update(
                """
                INSERT INTO usage_event_outbox (
                  event_id, family_id, customer_id, status, payload_json, retry_count,
                  next_retry_at, created_at, updated_at, deleted_at
                ) VALUES (
                  'evt_max_retry', 10, 1, 'PUBLISH_PENDING', '{}', 5, NULL,
                  TIMESTAMP '2026-03-16 09:56:00', TIMESTAMP '2026-03-16 10:00:00', NULL
                )
                """);

        List<UsageEventOutboxRow> rows =
                repository.pollPublishableRows(10, 5, LocalDateTime.of(2026, 3, 16, 10, 0, 1));

        assertThat(rows)
                .extracting(UsageEventOutboxRow::eventId)
                .containsExactly("evt_retry_due", "evt_pending");
    }

    @Test
    @DisplayName("markSent, markPendingForRetry, markFailed - 상태와 재시도 정보를 갱신한다")
    void markMethods_updateState() {
        jdbcTemplate.update(
                """
                INSERT INTO usage_event_outbox (
                  event_id, family_id, customer_id, status, payload_json, retry_count,
                  next_retry_at, created_at, updated_at, deleted_at
                ) VALUES (
                  'evt_1', 10, 1, 'PUBLISH_PENDING', '{}', 0, NULL,
                  TIMESTAMP '2026-03-16 10:00:00', TIMESTAMP '2026-03-16 10:00:00', NULL
                )
                """);

        repository.markPendingForRetry(
                1L,
                2,
                LocalDateTime.of(2026, 3, 16, 10, 5),
                "temporary failure",
                LocalDateTime.of(2026, 3, 16, 10, 1));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM usage_event_outbox WHERE id = 1", String.class))
                .isEqualTo(UsageEventOutboxStatus.PUBLISH_PENDING.name());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT retry_count FROM usage_event_outbox WHERE id = 1",
                                Integer.class))
                .isEqualTo(2);

        repository.markFailed(1L, 5, "permanent failure", LocalDateTime.of(2026, 3, 16, 10, 2));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM usage_event_outbox WHERE id = 1", String.class))
                .isEqualTo(UsageEventOutboxStatus.FAILED.name());

        repository.markSent(1L, LocalDateTime.of(2026, 3, 16, 10, 3));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM usage_event_outbox WHERE id = 1", String.class))
                .isEqualTo(UsageEventOutboxStatus.SENT.name());
    }

    @Test
    @DisplayName("countByStatus, findOldestPendingCreatedAt - 운영 조회용 값을 반환한다")
    void countAndAgeQueries_returnOperationalValues() {
        jdbcTemplate.update(
                """
                INSERT INTO usage_event_outbox (
                  event_id, family_id, customer_id, status, payload_json, retry_count,
                  next_retry_at, created_at, updated_at, deleted_at
                ) VALUES
                  ('evt_pending_1', 10, 1, 'PUBLISH_PENDING', '{}', 0, NULL,
                   TIMESTAMP '2026-03-16 09:55:00', TIMESTAMP '2026-03-16 09:55:00', NULL),
                  ('evt_pending_2', 10, 1, 'PUBLISH_PENDING', '{}', 1, TIMESTAMP '2026-03-16 10:30:00',
                   TIMESTAMP '2026-03-16 09:56:00', TIMESTAMP '2026-03-16 09:56:00', NULL),
                  ('evt_failed', 10, 1, 'FAILED', '{}', 2, NULL,
                   TIMESTAMP '2026-03-16 09:57:00', TIMESTAMP '2026-03-16 09:57:00', NULL),
                  ('evt_sent', 10, 1, 'SENT', '{}', 0, NULL,
                   TIMESTAMP '2026-03-16 09:58:00', TIMESTAMP '2026-03-16 09:58:00', NULL)
                """);

        assertThat(repository.countByStatus(UsageEventOutboxStatus.PUBLISH_PENDING)).isEqualTo(2);
        assertThat(repository.countByStatus(UsageEventOutboxStatus.FAILED)).isEqualTo(1);
        assertThat(repository.countByStatus(UsageEventOutboxStatus.SENT)).isEqualTo(1);
        assertThat(repository.findOldestPendingCreatedAt(LocalDateTime.of(2026, 3, 16, 10, 0)))
                .isEqualTo(LocalDateTime.of(2026, 3, 16, 9, 55));
    }

    private DataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:usage_event_outbox_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
