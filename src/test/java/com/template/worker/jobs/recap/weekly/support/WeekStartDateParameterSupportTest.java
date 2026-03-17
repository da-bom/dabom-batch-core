package com.template.worker.jobs.recap.weekly.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import com.template.worker.jobs.common.support.BatchJobConstants;

class WeekStartDateParameterSupportTest {

    private final WeekStartDateParameterSupport support = new WeekStartDateParameterSupport();

    @Test
    @DisplayName("resolveWeekStartDate - weekStartDate 파라미터가 있으면 해당 값을 반환한다")
    void resolveWeekStartDate_withParam_returnsParsedValue() {
        JobParameters parameters =
                new JobParametersBuilder()
                        .addString("weekStartDate", "2026-03-02")
                        .toJobParameters();

        LocalDate weekStartDate = support.resolveWeekStartDate(parameters);

        assertThat(weekStartDate).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("resolveWeekStartDate - 파라미터가 없으면 직전 주 월요일을 반환한다")
    void resolveWeekStartDate_withoutParam_returnsPreviousWeekMonday() {
        JobParameters parameters = new JobParametersBuilder().toJobParameters();

        LocalDate weekStartDate = support.resolveWeekStartDate(parameters);

        LocalDate expected =
                ZonedDateTime.now(BatchJobConstants.KST_ZONE_ID)
                        .toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .minusWeeks(1);
        assertThat(weekStartDate).isEqualTo(expected);
    }

    @Test
    @DisplayName("resolveWeekStartDate - 월요일이 아니면 예외가 발생한다")
    void resolveWeekStartDate_withNonMonday_throwsException() {
        assertThatThrownBy(() -> support.resolveWeekStartDate("2026-03-03"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekStartDate must be Monday");
    }

    @Test
    @DisplayName("resolveWeekStartDate - 포맷이 잘못되면 예외가 발생한다")
    void resolveWeekStartDate_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> support.resolveWeekStartDate("2026/03/02"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid weekStartDate format");
    }
}
