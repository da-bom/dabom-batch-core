package com.template.worker.jobs.reconciliation.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobParameterSupport;

@ExtendWith(MockitoExtension.class)
class ReconciliationFamilyKeyInvalidationWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisKeyGenerator keyGenerator;
    @Mock private DbRedisReconciliationJobParameterSupport parameterSupport;

    @InjectMocks private ReconciliationFamilyKeyInvalidationWriter writer;

    @Test
    @DisplayName(
            "write - targetMonth suffix family info와 remaining 키만 삭제하고 step context에 삭제 건수를 기록한다")
    void write_deletesOnlyTargetFamilyKeys_andStoresCountsInContext() {
        JobParameters jobParameters =
                new JobParametersBuilder().addString("targetMonth", "2026-03-01").toJobParameters();
        JobInstance jobInstance = new JobInstance(1L, "dbRedisReconciliationJob");
        JobExecution jobExecution = new JobExecution(jobInstance, jobParameters);
        StepExecution stepExecution = new StepExecution("invalidate-family-step", jobExecution);

        LocalDate targetMonth = LocalDate.of(2026, 3, 1);
        when(parameterSupport.resolveTargetMonth(any(JobParameters.class))).thenReturn(targetMonth);
        when(keyGenerator.familyInfoKey(10L, targetMonth)).thenReturn("family:10:info:202603");
        when(keyGenerator.familyRemainingKey(10L, targetMonth))
                .thenReturn("family:10:remaining:202603");
        when(keyGenerator.familyInfoKey(11L, targetMonth)).thenReturn("family:11:info:202603");
        when(keyGenerator.familyRemainingKey(11L, targetMonth))
                .thenReturn("family:11:remaining:202603");

        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            RedisCallback<Object> callback = invocation.getArgument(0);
                            StringRedisConnection connection = mock(StringRedisConnection.class);
                            callback.doInRedis(connection);

                            verify(connection).del("family:10:info:202603");
                            verify(connection).del("family:10:remaining:202603");
                            verify(connection).del("family:11:info:202603");
                            verify(connection).del("family:11:remaining:202603");

                            return List.of(1L, 1L, 0L, 1L);
                        });

        writer.beforeStep(stepExecution);
        writer.write(new Chunk<>(List.of(10L, 11L)));
        writer.afterStep(stepExecution);

        assertThat(
                        stepExecution
                                .getExecutionContext()
                                .getLong(
                                        DbRedisReconciliationJobConstants
                                                .STEP_CONTEXT_DELETED_FAMILY_INFO_COUNT))
                .isEqualTo(1L);
        assertThat(
                        stepExecution
                                .getExecutionContext()
                                .getLong(
                                        DbRedisReconciliationJobConstants
                                                .STEP_CONTEXT_DELETED_FAMILY_REMAINING_COUNT))
                .isEqualTo(2L);

        verify(parameterSupport).resolveTargetMonth(jobParameters);
        verify(keyGenerator, never()).familyAlertThresholdKey(anyLong(), anyInt(), any());
    }
}
