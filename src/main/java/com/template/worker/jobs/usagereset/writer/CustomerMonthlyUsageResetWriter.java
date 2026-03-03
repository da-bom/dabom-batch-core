package com.template.worker.jobs.usagereset.writer;

import java.time.LocalDate;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.usagereset.model.FamilyMemberUsageResetTarget;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetRedisKeyGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomerMonthlyUsageResetWriter
        implements ItemWriter<FamilyMemberUsageResetTarget>, StepExecutionListener {

    private static final String RESET_VALUE = "0";

    private final StringRedisTemplate redisTemplate;
    private final MonthlyUsageResetRedisKeyGenerator keyGenerator;
    private final MonthlyUsageResetJobParameterSupport parameterSupport;

    private long nextMonthStartEpochSecond;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // targetMonth 기준 다음 달 1일 00:00 만료 시각을 계산함
        LocalDate targetMonth =
                parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
        nextMonthStartEpochSecond = parameterSupport.resolveNextMonthStartEpochSecond(targetMonth);
    }

    @Override
    public void write(Chunk<? extends FamilyMemberUsageResetTarget> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // Redis 파이프라인으로 월사용량 초기화와 만료 설정을 일괄 처리함
        redisTemplate.executePipelined(
                (RedisCallback<Object>)
                        connection -> {
                            StringRedisConnection redisConnection =
                                    (StringRedisConnection) connection;
                            for (FamilyMemberUsageResetTarget target : chunk) {
                                String monthlyUsageKey =
                                        keyGenerator.customerMonthlyUsageKey(
                                                target.familyId(), target.customerId());
                                // 월사용량 키를 0으로 초기화하고 다음 달 시작 시각으로 만료를 설정함
                                redisConnection.set(monthlyUsageKey, RESET_VALUE);
                                redisConnection.expireAt(
                                        monthlyUsageKey, nextMonthStartEpochSecond);
                            }
                            return null;
                        });
    }
}
