package com.template.worker.global.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

class BatchRetrySupportTest {

    @Test
    @DisplayName("createDbRetryTemplate - QueryTimeoutException 은 설정 횟수까지 재시도한다")
    void createDbRetryTemplate_retriesQueryTimeoutException() {
        BatchRetrySupport batchRetrySupport = new BatchRetrySupport(3, 0L);
        AtomicInteger attempts = new AtomicInteger();

        String result =
                batchRetrySupport
                        .createDbRetryTemplate()
                        .execute(
                                retryContext -> {
                                    if (attempts.incrementAndGet() < 3) {
                                        throw new QueryTimeoutException("timeout");
                                    }
                                    return "success";
                                });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(3);
        assertThat(batchRetrySupport.getRetryLimit()).isEqualTo(3);
        assertThat(batchRetrySupport.getBackOffMillis()).isZero();
    }

    @Test
    @DisplayName("createDbRetryTemplate - 비재시도 예외는 즉시 전파한다")
    void createDbRetryTemplate_doesNotRetryNonRetryableException() {
        BatchRetrySupport batchRetrySupport = new BatchRetrySupport(3, 0L);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executeNonRetryableFailure(batchRetrySupport, attempts))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("createRedisRetryTemplate - RedisConnectionFailureException 은 설정 횟수까지 재시도한다")
    void createRedisRetryTemplate_retriesRedisConnectionFailureException() {
        BatchRetrySupport batchRetrySupport = new BatchRetrySupport(3, 0L);
        AtomicInteger attempts = new AtomicInteger();

        batchRetrySupport
                .createRedisRetryTemplate()
                .execute(
                        retryContext -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new RedisConnectionFailureException("redis");
                            }
                            return null;
                        });

        assertThat(attempts).hasValue(3);
    }

    private void executeNonRetryableFailure(
            BatchRetrySupport batchRetrySupport, AtomicInteger attempts) {
        batchRetrySupport
                .createDbRetryTemplate()
                .execute(
                        retryContext -> {
                            attempts.incrementAndGet();
                            throw new IllegalArgumentException("bad");
                        });
    }
}
