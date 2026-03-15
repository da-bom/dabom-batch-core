package com.template.worker.jobs.usagereset.writer;

import java.time.LocalDate;
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
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FamilyRedisResetWriter implements ItemWriter<Long>, StepExecutionListener {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;
    private final MonthlyUsageResetJobParameterSupport parameterSupport;

    private LocalDate previousMonth;
    private long deletedFamilyKeyCount;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        LocalDate targetMonth =
                parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
        previousMonth = targetMonth.minusMonths(1);
        deletedFamilyKeyCount = 0L;
    }

    @Override
    public void write(Chunk<? extends Long> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // Redis 라운드트립을 줄이기 위해 전월 suffix 키를 파이프라인으로 일괄 삭제
        List<Object> results =
                redisTemplate.executePipelined(
                        (RedisCallback<Object>)
                                connection -> {
                                    StringRedisConnection redisConnection =
                                            (StringRedisConnection) connection;
                                    for (Long familyId : chunk) {
                                        String[] keysToDelete =
                                                new String
                                                        [2
                                                                + MonthlyUsageResetJobConstants
                                                                        .ALERT_THRESHOLDS
                                                                        .size()];
                                        keysToDelete[0] =
                                                keyGenerator.familyInfoKey(familyId, previousMonth);
                                        keysToDelete[1] =
                                                keyGenerator.familyRemainingKey(
                                                        familyId, previousMonth);

                                        int keyIndex = 2;
                                        for (Integer threshold :
                                                MonthlyUsageResetJobConstants.ALERT_THRESHOLDS) {
                                            keysToDelete[keyIndex++] =
                                                    keyGenerator.familyAlertThresholdKey(
                                                            familyId, threshold, previousMonth);
                                        }

                                        redisConnection.del(keysToDelete);
                                    }
                                    return null;
                                });

        for (Object result : results) {
            deletedFamilyKeyCount += resolveDeletedCount(result);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stepExecution
                .getExecutionContext()
                .putLong(
                        MonthlyUsageResetJobConstants.STEP_CONTEXT_DELETED_FAMILY_KEY_COUNT,
                        deletedFamilyKeyCount);
        return null;
    }

    private long resolveDeletedCount(Object rawResult) {
        if (rawResult instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
