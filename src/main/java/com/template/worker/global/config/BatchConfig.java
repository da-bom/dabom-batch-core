package com.template.worker.global.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;

/** - 모든 배치 프로젝트는 이 설정을 기반으로 실행된다. - 실제 배치 Job 구현은 example 혹은 각 도메인 배치 프로젝트에서 정의한다. */
@Configuration
@EnableBatchProcessing
public class BatchConfig {}
