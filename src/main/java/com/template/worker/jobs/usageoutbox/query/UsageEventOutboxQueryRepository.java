package com.template.worker.jobs.usageoutbox.query;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.template.worker.jobs.usageoutbox.model.UsageEventOutboxRow;
import com.template.worker.jobs.usageoutbox.support.UsageEventOutboxStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UsageEventOutboxQueryRepository {

    private static final String PARAM_ID = "id";
    private static final String PARAM_STATUS = "status";
    private static final String PARAM_NOW = "now";
    private static final String PARAM_UPDATED_AT = "updatedAt";
    private static final String PARAM_RETRY_COUNT = "retryCount";
    private static final String PARAM_NEXT_RETRY_AT = "nextRetryAt";
    private static final String PARAM_LAST_ERROR = "lastError";
    private static final String PARAM_PUBLISH_PENDING = "publishPending";
    private static final String PARAM_FAILED = "failed";
    private static final String PARAM_SENT = "sent";
    private static final String PARAM_BATCH_SIZE = "batchSize";
    private static final String PARAM_MAX_RETRY = "maxRetry";

    private static final String POLL_SQL =
            """
            SELECT id,
                   event_id,
                   payload_json,
                   retry_count
            FROM usage_event_outbox
            WHERE deleted_at IS NULL
              AND status = :publishPending
              AND retry_count < :maxRetry
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY created_at ASC
            LIMIT :batchSize
            """;

    private static final String MARK_SENT_SQL =
            """
            UPDATE usage_event_outbox
            SET status = :sent,
                next_retry_at = NULL,
                last_error = NULL,
                updated_at = :updatedAt
            WHERE id = :id
            """;

    private static final String MARK_PENDING_FOR_RETRY_SQL =
            """
            UPDATE usage_event_outbox
            SET status = :publishPending,
                retry_count = :retryCount,
                next_retry_at = :nextRetryAt,
                last_error = :lastError,
                updated_at = :updatedAt
            WHERE id = :id
            """;

    private static final String MARK_FAILED_SQL =
            """
            UPDATE usage_event_outbox
            SET status = :failed,
                retry_count = :retryCount,
                next_retry_at = NULL,
                last_error = :lastError,
                updated_at = :updatedAt
            WHERE id = :id
            """;

    private static final RowMapper<UsageEventOutboxRow> ROW_MAPPER =
            (resultSet, rowNum) ->
                    new UsageEventOutboxRow(
                            resultSet.getLong("id"),
                            resultSet.getString("event_id"),
                            resultSet.getString("payload_json"),
                            resultSet.getInt("retry_count"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<UsageEventOutboxRow> pollPublishableRows(
            int batchSize, int maxRetry, LocalDateTime now) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(PARAM_BATCH_SIZE, batchSize)
                        .addValue(PARAM_MAX_RETRY, maxRetry)
                        .addValue(PARAM_NOW, Timestamp.valueOf(now))
                        .addValue(
                                PARAM_PUBLISH_PENDING,
                                UsageEventOutboxStatus.PUBLISH_PENDING.name());
        return jdbcTemplate.query(POLL_SQL, params, ROW_MAPPER);
    }

    public void markSent(long id, LocalDateTime updatedAt) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(PARAM_ID, id)
                        .addValue(PARAM_SENT, UsageEventOutboxStatus.SENT.name())
                        .addValue(PARAM_UPDATED_AT, Timestamp.valueOf(updatedAt));
        jdbcTemplate.update(MARK_SENT_SQL, params);
    }

    public void markPendingForRetry(
            long id,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lastError,
            LocalDateTime updatedAt) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(PARAM_ID, id)
                        .addValue(
                                PARAM_PUBLISH_PENDING,
                                UsageEventOutboxStatus.PUBLISH_PENDING.name())
                        .addValue(PARAM_RETRY_COUNT, retryCount)
                        .addValue(PARAM_NEXT_RETRY_AT, Timestamp.valueOf(nextRetryAt))
                        .addValue(PARAM_LAST_ERROR, truncate(lastError))
                        .addValue(PARAM_UPDATED_AT, Timestamp.valueOf(updatedAt));
        jdbcTemplate.update(MARK_PENDING_FOR_RETRY_SQL, params);
    }

    public void markFailed(long id, int retryCount, String lastError, LocalDateTime updatedAt) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(PARAM_ID, id)
                        .addValue(PARAM_FAILED, UsageEventOutboxStatus.FAILED.name())
                        .addValue(PARAM_RETRY_COUNT, retryCount)
                        .addValue(PARAM_LAST_ERROR, truncate(lastError))
                        .addValue(PARAM_UPDATED_AT, Timestamp.valueOf(updatedAt));
        jdbcTemplate.update(MARK_FAILED_SQL, params);
    }

    public int countByStatus(UsageEventOutboxStatus status) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue(PARAM_STATUS, status.name());
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM usage_event_outbox
                        WHERE deleted_at IS NULL
                          AND status = :status
                        """,
                        params,
                        Integer.class);
        return count == null ? 0 : count;
    }

    public LocalDateTime findOldestPendingCreatedAt(LocalDateTime now) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(
                                PARAM_PUBLISH_PENDING,
                                UsageEventOutboxStatus.PUBLISH_PENDING.name())
                        .addValue(PARAM_NOW, Timestamp.valueOf(now));
        Timestamp timestamp =
                jdbcTemplate.queryForObject(
                        """
                        SELECT MIN(created_at)
                        FROM usage_event_outbox
                        WHERE deleted_at IS NULL
                          AND status = :publishPending
                          AND (next_retry_at IS NULL OR next_retry_at <= :now)
                        """,
                        params,
                        Timestamp.class);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
