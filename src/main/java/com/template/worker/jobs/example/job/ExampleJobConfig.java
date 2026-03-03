package com.template.worker.jobs.example.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.template.worker.global.listener.JobResultListener;
import com.template.worker.jobs.example.step.ExampleStepConfig;

import lombok.RequiredArgsConstructor;

/** 이 클래스를 복사하여 실제 배치 Job을 만든다. */
@Configuration
@RequiredArgsConstructor
public class ExampleJobConfig {
    private final JobRepository jobRepository;
    private final JobResultListener jobResultListener;
    private final ExampleStepConfig stepConfig;

    @Bean
    public Job exampleJob() {
        return new JobBuilder("example-job", jobRepository)
                .listener(jobResultListener)
                .start(stepConfig.exampleStep())
                .build();
    }
}
