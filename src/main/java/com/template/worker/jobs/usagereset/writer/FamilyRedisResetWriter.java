package com.template.worker.jobs.usagereset.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.global.util.RedisKeyGenerator;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FamilyRedisResetWriter implements ItemWriter<Long> {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    @Override
    public void write(Chunk<? extends Long> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // Redis 라운드트립을 줄이기 위해 파이프라인으로 일괄 삭제함
        redisTemplate.executePipelined(
                (RedisCallback<Object>)
                        connection -> {
                            StringRedisConnection redisConnection =
                                    (StringRedisConnection) connection;
                            for (Long familyId : chunk) {
                                String[] keysToDelete =
                                        new String
                                                [1
                                                        + MonthlyUsageResetJobConstants
                                                                .ALERT_THRESHOLDS
                                                                .size()];
                                keysToDelete[0] = keyGenerator.familyRemainingKey(familyId);

                                int keyIndex = 1;
                                for (Integer threshold :
                                        MonthlyUsageResetJobConstants.ALERT_THRESHOLDS) {
                                    keysToDelete[keyIndex++] =
                                            keyGenerator.familyAlertThresholdKey(
                                                    familyId, threshold);
                                }

                                // remaining 및 임계치 alert 키를 함께 초기화함
                                redisConnection.del(keysToDelete);
                            }
                            return null;
                        });
    }
}
