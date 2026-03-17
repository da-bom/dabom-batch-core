package com.template.worker.jobs.recap.monthly.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import com.template.worker.jobs.common.support.BatchJobConstants;
import com.template.worker.jobs.common.support.TargetMonthParameterSupport;

class MonthlyFamilyRecapJobParameterSupportTest {

    private final MonthlyFamilyRecapJobParameterSupport support =
            new MonthlyFamilyRecapJobParameterSupport(new TargetMonthParameterSupport());

    @Test
    @DisplayName("resolveTargetMonth - targetMonth 파라미터가 있으면 해당 값을 반환한다")
    void resolveTargetMonth_withParam_returnsParsedValue() {
        JobParameters parameters =
                new JobParametersBuilder().addString("targetMonth", "2026-03-01").toJobParameters();

        LocalDate targetMonth = support.resolveTargetMonth(parameters);

        assertThat(targetMonth).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("resolveTargetMonth - 파라미터가 없으면 직전 달 1일을 반환한다")
    void resolveTargetMonth_withoutParam_returnsPreviousMonthStart() {
        JobParameters parameters = new JobParametersBuilder().toJobParameters();

        LocalDate targetMonth = support.resolveTargetMonth(parameters);

        LocalDate expected =
                ZonedDateTime.now(BatchJobConstants.KST_ZONE_ID)
                        .toLocalDate()
                        .withDayOfMonth(1)
                        .minusMonths(1);
        assertThat(targetMonth).isEqualTo(expected);
    }

    @Test
    @DisplayName("resolveTargetMonth - 1일이 아니면 예외가 발생한다")
    void resolveTargetMonth_withNonFirstDay_throwsException() {
        assertThatThrownBy(() -> support.resolveTargetMonth("2026-03-02"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first day of month");
    }

    @Test
    @DisplayName("resolveTargetMonth - 포맷이 잘못되면 예외가 발생한다")
    void resolveTargetMonth_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> support.resolveTargetMonth("2026/03/01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid targetMonth format");
    }
}
