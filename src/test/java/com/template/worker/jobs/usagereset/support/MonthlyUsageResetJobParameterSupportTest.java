package com.template.worker.jobs.usagereset.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

class MonthlyUsageResetJobParameterSupportTest {

    private final MonthlyUsageResetJobParameterSupport support =
            new MonthlyUsageResetJobParameterSupport();

    @Test
    @DisplayName("resolveTargetMonth - targetMonth 파라미터가 있으면 해당 값을 반환한다")
    void resolveTargetMonth_withParam_returnsParsedValue() {
        // given
        JobParameters parameters =
                new JobParametersBuilder().addString("targetMonth", "2026-03-01").toJobParameters();

        // when
        LocalDate targetMonth = support.resolveTargetMonth(parameters);

        // then
        assertThat(targetMonth).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("resolveTargetMonth - 파라미터가 없으면 현재 월 1일을 반환한다")
    void resolveTargetMonth_withoutParam_returnsCurrentMonthStart() {
        // given
        JobParameters parameters = new JobParametersBuilder().toJobParameters();

        // when
        LocalDate targetMonth = support.resolveTargetMonth(parameters);

        // then
        LocalDate expected =
                ZonedDateTime.now(MonthlyUsageResetJobConstants.KST_ZONE_ID)
                        .toLocalDate()
                        .withDayOfMonth(1);
        assertThat(targetMonth).isEqualTo(expected);
    }

    @Test
    @DisplayName("resolveTargetMonth - day가 1이 아니면 예외가 발생한다")
    void resolveTargetMonth_withInvalidDay_throwsException() {
        assertThatThrownBy(() -> support.resolveTargetMonth("2026-03-05"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetMonth must be first day of month");
    }
}
