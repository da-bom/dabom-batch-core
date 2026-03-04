package com.template.worker.jobs.usagereset.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.global.util.RedisKeyGenerator;
import com.template.worker.jobs.usagereset.model.FamilyMemberUsageResetTarget;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomerMonthlyUsageResetWriter implements ItemWriter<FamilyMemberUsageResetTarget> {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    @Override
    public void write(Chunk<? extends FamilyMemberUsageResetTarget> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // Redis 파이프라인으로 고객 월사용량 키를 일괄 삭제함
        redisTemplate.executePipelined(
                (RedisCallback<Object>)
                        connection -> {
                            StringRedisConnection redisConnection =
                                    (StringRedisConnection) connection;
                            for (FamilyMemberUsageResetTarget target : chunk) {
                                String monthlyUsageKey =
                                        keyGenerator.customerMonthlyUsageKey(
                                                target.familyId(), target.customerId());
                                redisConnection.del(monthlyUsageKey);
                            }
                            return null;
                        });
    }
}
