package com.template.worker.jobs.reconciliation.support;

import java.time.Duration;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "batch.jobs.db-redis-reconciliation")
public class DbRedisReconciliationProperties {

    // 분산 락 TTL. 스텝 체인 최대 수행 시간을 고려해 설정함
    private Duration lockTtl = Duration.ofHours(1);

    // Redis DEL pipeline chunk 크기
    @Min(1)
    private int redisChunkSize = 2000;

    // PK cursor reader 조회 배치 크기
    @Min(1)
    private int dbFetchSize = 4000;
}
