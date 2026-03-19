package com.template.worker.jobs.recap.weekly.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.template.worker.jobs.recap.weekly.model.WeeklyFamilyRecapRow;
import com.template.worker.jobs.recap.weekly.processor.WeeklyFamilyRecapProcessor;

class WeeklyFamilyRecapUpsertWriterTest {

    private JdbcTemplate jdbcTemplate;
    private WeeklyFamilyRecapUpsertWriter writer;
    private WeeklyFamilyRecapProcessor processor;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:weekly_recap_writer_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        processor = mock(WeeklyFamilyRecapProcessor.class);
        writer =
                new WeeklyFamilyRecapUpsertWriter(
                        new NamedParameterJdbcTemplate(dataSource), processor);

        jdbcTemplate.execute("DROP TABLE IF EXISTS family_recap_weekly");
        jdbcTemplate.execute(
                """
                CREATE TABLE family_recap_weekly (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  family_id BIGINT NOT NULL,
                  week_start_date DATE NOT NULL,
                  total_used_bytes BIGINT NOT NULL,
                  total_quota_bytes BIGINT NOT NULL,
                  usage_rate_percent DECIMAL(5,2) NOT NULL,
                  usage_by_weekday VARCHAR(2000) NOT NULL,
                  peak_usage VARCHAR(1000),
                  mission_created_count INT NOT NULL,
                  mission_completed_count INT NOT NULL,
                  mission_rejected_count INT NOT NULL,
                  total_appeal_count INT NOT NULL,
                  approved_appeal_count INT NOT NULL,
                  rejected_appeal_count INT NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_family_recap_weekly_family_week (family_id, week_start_date)
                )
                """);
    }

    @Test
    @DisplayName("write - 동일 family/week 재실행 시 row 증가 없이 값만 갱신한다")
    void write_sameFamilyAndWeek_upsertsWithoutIncreasingRows() {
        WeeklyFamilyRecapRow firstRow =
                new WeeklyFamilyRecapRow(
                        10L,
                        LocalDate.of(2026, 3, 2),
                        100L,
                        1000L,
                        new BigDecimal("10.00"),
                        "{\"monday\":10.00,\"tuesday\":20.00,\"wednesday\":30.00,"
                                + "\"thursday\":10.00,\"friday\":10.00,\"saturday\":10.00,"
                                + "\"sunday\":10.00}",
                        "{\"startHour\":20,\"endHour\":21,\"peakBytes\":100}",
                        1,
                        1,
                        0,
                        1,
                        1,
                        0);

        WeeklyFamilyRecapRow secondRow =
                new WeeklyFamilyRecapRow(
                        10L,
                        LocalDate.of(2026, 3, 2),
                        250L,
                        1000L,
                        new BigDecimal("25.00"),
                        "{\"monday\":25.00,\"tuesday\":25.00,\"wednesday\":25.00,"
                                + "\"thursday\":25.00,\"friday\":0.00,\"saturday\":0.00,"
                                + "\"sunday\":0.00}",
                        "{\"startHour\":21,\"endHour\":22,\"peakBytes\":250}",
                        2,
                        2,
                        1,
                        3,
                        2,
                        1);

        when(processor.processAll(List.of(10L))).thenReturn(List.of(firstRow), List.of(secondRow));

        writer.write(new Chunk<>(List.of(10L)));
        writer.write(new Chunk<>(List.of(10L)));

        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM family_recap_weekly", Integer.class);
        Long totalUsedBytes =
                jdbcTemplate.queryForObject(
                        "SELECT total_used_bytes FROM family_recap_weekly "
                                + "WHERE family_id = 10 AND week_start_date = '2026-03-02'",
                        Long.class);
        Integer totalAppealCount =
                jdbcTemplate.queryForObject(
                        "SELECT total_appeal_count FROM family_recap_weekly "
                                + "WHERE family_id = 10 AND week_start_date = '2026-03-02'",
                        Integer.class);
        Integer approvedAppealCount =
                jdbcTemplate.queryForObject(
                        "SELECT approved_appeal_count FROM family_recap_weekly "
                                + "WHERE family_id = 10 AND week_start_date = '2026-03-02'",
                        Integer.class);
        Integer rejectedAppealCount =
                jdbcTemplate.queryForObject(
                        "SELECT rejected_appeal_count FROM family_recap_weekly "
                                + "WHERE family_id = 10 AND week_start_date = '2026-03-02'",
                        Integer.class);

        assertThat(rowCount).isEqualTo(1);
        assertThat(totalUsedBytes).isEqualTo(250L);
        assertThat(totalAppealCount).isEqualTo(3);
        assertThat(approvedAppealCount).isEqualTo(2);
        assertThat(rejectedAppealCount).isEqualTo(1);
    }
}
