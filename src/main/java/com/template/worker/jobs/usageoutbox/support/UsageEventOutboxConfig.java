package com.template.worker.jobs.usageoutbox.support;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UsageEventOutboxConfig {

    /** Outbox 발행 작업에서 사용할 고정 크기 워커 풀을 생성한다. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService usageEventOutboxExecutor(UsageEventOutboxProperties properties) {
        return Executors.newFixedThreadPool(properties.getConcurrency());
    }
}
