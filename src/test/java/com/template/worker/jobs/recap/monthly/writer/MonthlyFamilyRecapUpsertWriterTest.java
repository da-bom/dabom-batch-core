package com.template.worker.jobs.recap.monthly.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.template.worker.jobs.recap.monthly.model.MonthlyFamilyRecapRow;

class MonthlyFamilyRecapUpsertWriterTest {

    private JdbcTemplate jdbcTemplate;
    private MonthlyFamilyRecapUpsertWriter writer;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:monthly_recap_writer_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        writer = new MonthlyFamilyRecapUpsertWriter(new NamedParameterJdbcTemplate(dataSource));

        jdbcTemplate.execute("DROP TABLE IF EXISTS family_recap_monthly");
        jdbcTemplate.execute(
                """
                CREATE TABLE family_recap_monthly (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  family_id BIGINT NOT NULL,
                  report_month DATE NOT NULL,
                  total_used_bytes BIGINT NOT NULL,
                  total_quota_bytes BIGINT NOT NULL,
                  usage_rate_percent DECIMAL(5,2) NOT NULL,
                  usage_by_weekday VARCHAR(2000),
                  peak_usage VARCHAR(1000),
                  mission_summary_json VARCHAR(2000),
                  appeal_summary_json VARCHAR(2000),
                  appeal_highlights_json VARCHAR(4000),
                  communication_score DECIMAL(5,2),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_family_recap_monthly_family_month (family_id, report_month)
                )
                """);
    }

    @Test
    @DisplayName("write - 동일 family/month 재실행 시 row 증가 없이 값만 갱신한다")
    void write_sameFamilyAndMonth_upsertsWithoutIncreasingRows() {
        MonthlyFamilyRecapRow firstRow =
                new MonthlyFamilyRecapRow(
                        10L,
                        LocalDate.of(2026, 3, 1),
                        100L,
                        1000L,
                        new BigDecimal("10.00"),
                        "{\"monday\":10.00}",
                        "{\"startHour\":20,\"endHour\":21,\"mostUsedWeekday\":\"monday\"}",
                        "{\"totalMissionCount\":1,\"completedMissionCount\":1,\"rejectedRequestCount\":0}",
                        "{\"totalAppeals\":1,\"approvedAppeals\":1,\"rejectedAppeals\":0}",
                        "{\"topSuccessfulRequester\":{},\"topAcceptedApprover\":{}}",
                        null);

        MonthlyFamilyRecapRow secondRow =
                new MonthlyFamilyRecapRow(
                        10L,
                        LocalDate.of(2026, 3, 1),
                        250L,
                        1000L,
                        new BigDecimal("25.00"),
                        "{\"monday\":25.00}",
                        "{\"startHour\":21,\"endHour\":22,\"mostUsedWeekday\":\"tuesday\"}",
                        "{\"totalMissionCount\":2,\"completedMissionCount\":2,\"rejectedRequestCount\":0}",
                        "{\"totalAppeals\":2,\"approvedAppeals\":1,\"rejectedAppeals\":1}",
                        "{\"topSuccessfulRequester\":{},\"topAcceptedApprover\":{}}",
                        new BigDecimal("88.88"));

        writer.write(new Chunk<>(List.of(firstRow)));
        writer.write(new Chunk<>(List.of(secondRow)));

        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM family_recap_monthly", Integer.class);
        Long totalUsedBytes =
                jdbcTemplate.queryForObject(
                        "SELECT total_used_bytes FROM family_recap_monthly "
                                + "WHERE family_id = 10 AND report_month = '2026-03-01'",
                        Long.class);
        BigDecimal communicationScore =
                jdbcTemplate.queryForObject(
                        "SELECT communication_score FROM family_recap_monthly "
                                + "WHERE family_id = 10 AND report_month = '2026-03-01'",
                        BigDecimal.class);

        assertThat(rowCount).isEqualTo(1);
        assertThat(totalUsedBytes).isEqualTo(250L);
        assertThat(communicationScore).isEqualByComparingTo("88.88");
    }
}
