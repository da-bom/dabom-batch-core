package com.template.worker.jobs.usagereset.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.template.worker.global.util.RedisKeyGenerator;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class FamilyRedisResetWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisKeyGenerator keyGenerator;
    @Mock private MonthlyUsageResetJobParameterSupport parameterSupport;

    @InjectMocks private FamilyRedisResetWriter writer;

    @Test
    @DisplayName("write - 전월 suffix family info와 remaining과 alert 키만 삭제한다")
    void write_deletesOnlyPreviousMonthFamilyKeys() {
        JobParameters jobParameters =
                new JobParametersBuilder().addString("targetMonth", "2026-03-01").toJobParameters();
        JobInstance jobInstance = new JobInstance(1L, "monthly-usage-reset-job");
        JobExecution jobExecution = new JobExecution(jobInstance, jobParameters);
        StepExecution stepExecution = new StepExecution("reset-family-redis-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        LocalDate previousMonth = LocalDate.of(2026, 2, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(keyGenerator.familyInfoKey(10L, previousMonth)).thenReturn("family:10:info:202602");
        when(keyGenerator.familyRemainingKey(10L, previousMonth))
                .thenReturn("family:10:remaining:202602");
        when(keyGenerator.familyAlertThresholdKey(10L, 10, previousMonth))
                .thenReturn("family:10:alert:THRESHOLD:10:202602");
        when(keyGenerator.familyAlertThresholdKey(10L, 30, previousMonth))
                .thenReturn("family:10:alert:THRESHOLD:30:202602");
        when(keyGenerator.familyAlertThresholdKey(10L, 50, previousMonth))
                .thenReturn("family:10:alert:THRESHOLD:50:202602");

        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            RedisCallback<Object> callback = invocation.getArgument(0);
                            StringRedisConnection connection = mock(StringRedisConnection.class);
                            callback.doInRedis(connection);

                            verify(connection)
                                    .del(
                                            "family:10:info:202602",
                                            "family:10:remaining:202602",
                                            "family:10:alert:THRESHOLD:10:202602",
                                            "family:10:alert:THRESHOLD:30:202602",
                                            "family:10:alert:THRESHOLD:50:202602");
                            return List.of(4L);
                        });

        writer.beforeStep(stepExecution);
        writer.write(new Chunk<>(List.of(10L)));
        writer.afterStep(stepExecution);

        assertThat(
                        stepExecution
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsageResetJobConstants
                                                .STEP_CONTEXT_DELETED_FAMILY_KEY_COUNT))
                .isEqualTo(4L);
    }
}
