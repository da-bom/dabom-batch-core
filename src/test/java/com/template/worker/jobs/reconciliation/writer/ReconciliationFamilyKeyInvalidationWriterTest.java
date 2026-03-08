package com.template.worker.jobs.reconciliation.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.template.worker.global.util.RedisKeyGenerator;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;

@ExtendWith(MockitoExtension.class)
class ReconciliationFamilyKeyInvalidationWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisKeyGenerator keyGenerator;

    @InjectMocks private ReconciliationFamilyKeyInvalidationWriter writer;

    @Test
    @DisplayName("write - family info/remaining 키만 삭제하고 step context에 삭제 건수를 기록한다")
    void write_deletesOnlyTargetFamilyKeys_andStoresCountsInContext() {
        JobInstance jobInstance = new JobInstance(1L, "dbRedisReconciliationJob");
        JobExecution jobExecution =
                new JobExecution(jobInstance, new JobParametersBuilder().toJobParameters());
        StepExecution stepExecution = new StepExecution("invalidate-family-step", jobExecution);

        when(keyGenerator.familyInfoKey(10L)).thenReturn("family:10:info");
        when(keyGenerator.familyRemainingKey(10L)).thenReturn("family:10:remaining");
        when(keyGenerator.familyInfoKey(11L)).thenReturn("family:11:info");
        when(keyGenerator.familyRemainingKey(11L)).thenReturn("family:11:remaining");

        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            RedisCallback<Object> callback = invocation.getArgument(0);
                            StringRedisConnection connection = mock(StringRedisConnection.class);
                            callback.doInRedis(connection);

                            verify(connection).del("family:10:info");
                            verify(connection).del("family:10:remaining");
                            verify(connection).del("family:11:info");
                            verify(connection).del("family:11:remaining");

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

        verify(keyGenerator, never()).familyAlertThresholdKey(anyLong(), anyInt());
        verify(keyGenerator, never()).customerMonthlyUsageKey(anyLong(), anyLong());
    }
}
