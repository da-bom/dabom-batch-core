package com.template.worker.jobs.usagereset.support;

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
@ConfigurationProperties(prefix = "batch.jobs.monthly-usage-reset")
public class MonthlyUsageResetProperties {

    private Duration lockTtl = Duration.ofHours(1);

    @Min(1)
    private int redisChunkSize = 1000;

    @Min(1)
    private int dbFetchSize = 1000;
}
