package com.template.worker.jobs.usageprecreate.support;

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
@ConfigurationProperties(prefix = "batch.jobs.monthly-usage-precreate")
public class MonthlyUsagePrecreateProperties {

    private Duration lockTtl = Duration.ofHours(1);

    @Min(1)
    private int dbFetchSize = 4000;
}
