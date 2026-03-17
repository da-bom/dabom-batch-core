package com.template.worker.jobs.reconciliation.writer;

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
import com.template.worker.jobs.reconciliation.model.FamilyMemberReconciliationTarget;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobConstants;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationJobParameterSupport;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationCustomerMonthlyUsageInvalidationWriter
        implements ItemWriter<FamilyMemberReconciliationTarget>, StepExecutionListener {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;
    private final DbRedisReconciliationJobParameterSupport parameterSupport;

    private LocalDate targetMonth;
    private long deletedCustomerMonthlyUsageCount;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // targetMonth suffix 키 삭제를 위해 파라미터를 선계산하고 카운터를 초기화함
        targetMonth = parameterSupport.resolveTargetMonth(stepExecution.getJobParameters());
        deletedCustomerMonthlyUsageCount = 0L;
    }

    @Override
    public void write(Chunk<? extends FamilyMemberReconciliationTarget> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        // Redis 파이프라인으로 대상 월 monthly usage 키만 일괄 삭제함
        List<Object> results =
                redisTemplate.executePipelined(
                        (RedisCallback<Object>)
                                connection -> {
                                    StringRedisConnection redisConnection =
                                            (StringRedisConnection) connection;
                                    for (FamilyMemberReconciliationTarget target : chunk) {
                                        String monthlyUsageKey =
                                                keyGenerator.customerMonthlyUsageKey(
                                                        target.familyId(),
                                                        target.customerId(),
                                                        targetMonth);
                                        redisConnection.del(monthlyUsageKey);
                                    }
                                    return null;
                                });

        for (Object result : results) {
            deletedCustomerMonthlyUsageCount += resolveDeletedCount(result);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // afterJob 요약 로그에서 사용할 수 있도록 StepExecutionContext에 누적값을 기록함
        stepExecution
                .getExecutionContext()
                .putLong(
                        DbRedisReconciliationJobConstants
                                .STEP_CONTEXT_DELETED_CUSTOMER_MONTHLY_USAGE_COUNT,
                        deletedCustomerMonthlyUsageCount);
        return null;
    }

    private long resolveDeletedCount(Object rawResult) {
        if (rawResult instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
