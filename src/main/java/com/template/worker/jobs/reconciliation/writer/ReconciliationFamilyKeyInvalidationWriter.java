package com.template.worker.jobs.reconciliation.writer;

import java.util.List;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.global.util.RedisKeyGenerator;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationFamilyKeyInvalidationWriter
        implements ItemWriter<Long>, StepExecutionListener {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    private long deletedFamilyInfoCount;
    private long deletedFamilyRemainingCount;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // Step 단위 집계를 위해 삭제 카운터를 초기화함
        deletedFamilyInfoCount = 0L;
        deletedFamilyRemainingCount = 0L;
    }

    @Override
    public void write(Chunk<? extends Long> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // Redis 라운드트립을 줄이기 위해 파이프라인으로 일괄 삭제함
        List<Object> results =
                redisTemplate.executePipelined(
                        (RedisCallback<Object>)
                                connection -> {
                                    StringRedisConnection redisConnection =
                                            (StringRedisConnection) connection;
                                    for (Long familyId : chunk) {
                                        redisConnection.del(keyGenerator.familyInfoKey(familyId));
                                        redisConnection.del(
                                                keyGenerator.familyRemainingKey(familyId));
                                    }
                                    return null;
                                });

        for (int index = 0; index < results.size(); index++) {
            long deletedCount = resolveDeletedCount(results.get(index));
            // 파이프라인 결과 순서(info -> remaining)에 맞춰 삭제 건수를 분리 집계함
            if (index % 2 == 0) {
                deletedFamilyInfoCount += deletedCount;
            } else {
                deletedFamilyRemainingCount += deletedCount;
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // afterJob 요약 로그에서 사용할 수 있도록 StepExecutionContext에 누적값을 기록함
        stepExecution
                .getExecutionContext()
                .putLong(
                        DbRedisReconciliationJobConstants.STEP_CONTEXT_DELETED_FAMILY_INFO_COUNT,
                        deletedFamilyInfoCount);
        stepExecution
                .getExecutionContext()
                .putLong(
                        DbRedisReconciliationJobConstants
                                .STEP_CONTEXT_DELETED_FAMILY_REMAINING_COUNT,
                        deletedFamilyRemainingCount);
        return null;
    }

    private long resolveDeletedCount(Object rawResult) {
        if (rawResult instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
