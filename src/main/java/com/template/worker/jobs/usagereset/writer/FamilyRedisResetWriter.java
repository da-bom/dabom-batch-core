package com.template.worker.jobs.usagereset.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.usagecommon.util.RedisKeyGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FamilyRedisResetWriter implements ItemWriter<Long> {

    private static final int ALERT_THRESHOLD_10 = 10;
    private static final int ALERT_THRESHOLD_30 = 30;
    private static final int ALERT_THRESHOLD_50 = 50;

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
                                // remaining 및 임계치 alert 키를 함께 초기화함
                                redisConnection.del(
                                        keyGenerator.familyRemainingKey(familyId),
                                        keyGenerator.familyAlertThresholdKey(
                                                familyId, ALERT_THRESHOLD_10),
                                        keyGenerator.familyAlertThresholdKey(
                                                familyId, ALERT_THRESHOLD_30),
                                        keyGenerator.familyAlertThresholdKey(
                                                familyId, ALERT_THRESHOLD_50));
                            }
                            return null;
                        });
    }
}
