package com.template.worker.jobs.recap.monthly.support;

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
@ConfigurationProperties(prefix = "batch.jobs.monthly-family-recap")
public class MonthlyFamilyRecapProperties {

    // 분산 락 TTL
    private Duration lockTtl = Duration.ofHours(1);

    // 집계+업서트 청크 크기
    @Min(1)
    private int chunkSize = 1000;

    // 가족 ID 커서 조회 배치 크기
    @Min(1)
    private int dbFetchSize = 1000;
}
