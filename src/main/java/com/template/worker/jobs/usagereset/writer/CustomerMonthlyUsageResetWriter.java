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

import com.template.worker.common.util.RedisKeyGenerator;
import com.template.worker.jobs.usagereset.model.FamilyMemberUsageResetTarget;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobConstants;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetJobParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomerMonthlyUsageResetWriter
        implements ItemWriter<FamilyMemberUsageResetTarget>, StepExecutionListener {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;
    private final MonthlyUsageResetJobParameterSupport parameterSupport;

    private LocalDate previousMonth;
    private long deletedCustomerMonthlyUsageKeyCount;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        LocalDate targetMonth =
                parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
        previousMonth = targetMonth.minusMonths(1);
        deletedCustomerMonthlyUsageKeyCount = 0L;
    }

    @Override
    public void write(Chunk<? extends FamilyMemberUsageResetTarget> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // Redis 파이프라인으로 전월 suffix가 붙은 개인 월사용량 키만 일괄 삭제함
        List<Object> results =
                redisTemplate.executePipelined(
                        (RedisCallback<Object>)
                                connection -> {
                                    StringRedisConnection redisConnection =
                                            (StringRedisConnection) connection;
                                    for (FamilyMemberUsageResetTarget target : chunk) {
                                        String monthlyUsageKey =
                                                keyGenerator.customerMonthlyUsageKey(
                                                        target.familyId(),
                                                        target.customerId(),
                                                        previousMonth);
                                        redisConnection.del(monthlyUsageKey);
                                    }
                                    return null;
                                });

        for (Object result : results) {
            deletedCustomerMonthlyUsageKeyCount += resolveDeletedCount(result);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stepExecution
                .getExecutionContext()
                .putLong(
                        MonthlyUsageResetJobConstants
                                .STEP_CONTEXT_DELETED_CUSTOMER_MONTHLY_USAGE_KEY_COUNT,
                        deletedCustomerMonthlyUsageKeyCount);
        return null;
    }

    private long resolveDeletedCount(Object rawResult) {
        if (rawResult instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
