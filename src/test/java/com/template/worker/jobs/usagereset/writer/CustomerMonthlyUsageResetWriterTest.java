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

import com.template.worker.common.util.RedisKeyGenerator;
import com.template.worker.jobs.usagereset.model.FamilyMemberUsageResetTarget;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class CustomerMonthlyUsageResetWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisKeyGenerator keyGenerator;
    @Mock private MonthlyUsageResetJobParameterSupport parameterSupport;

    @InjectMocks private CustomerMonthlyUsageResetWriter writer;

    @Test
    @DisplayName("write - 전월 suffix customer monthly usage 키만 삭제한다")
    void write_deletesOnlyPreviousMonthCustomerMonthlyUsageKeys() {
        JobParameters jobParameters =
                new JobParametersBuilder().addString("targetMonth", "2026-03-01").toJobParameters();
        JobInstance jobInstance = new JobInstance(1L, "monthly-usage-reset-job");
        JobExecution jobExecution = new JobExecution(jobInstance, jobParameters);
        StepExecution stepExecution = new StepExecution("reset-customer-redis-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        LocalDate previousMonth = LocalDate.of(2026, 2, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(keyGenerator.customerMonthlyUsageKey(10L, 100L, previousMonth))
                .thenReturn("family:10:customer:100:usage:monthly:202602");
        when(keyGenerator.customerMonthlyUsageKey(10L, 101L, previousMonth))
                .thenReturn("family:10:customer:101:usage:monthly:202602");

        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            RedisCallback<Object> callback = invocation.getArgument(0);
                            StringRedisConnection connection = mock(StringRedisConnection.class);
                            callback.doInRedis(connection);

                            verify(connection).del("family:10:customer:100:usage:monthly:202602");
                            verify(connection).del("family:10:customer:101:usage:monthly:202602");
                            return List.of(1L, 0L);
                        });

        writer.beforeStep(stepExecution);
        writer.write(
                new Chunk<>(
                        List.of(
                                new FamilyMemberUsageResetTarget(10L, 100L),
                                new FamilyMemberUsageResetTarget(10L, 101L))));
        writer.afterStep(stepExecution);

        assertThat(
                        stepExecution
                                .getExecutionContext()
                                .getLong(
                                        MonthlyUsageResetJobConstants
                                                .STEP_CONTEXT_DELETED_CUSTOMER_MONTHLY_USAGE_KEY_COUNT))
                .isEqualTo(1L);
    }
}
