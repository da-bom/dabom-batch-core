package com.template.worker.jobs.example.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.template.worker.jobs.example.processor.ExampleItemProcessor;
import com.template.worker.jobs.example.reader.ExampleItemReader;
import com.template.worker.jobs.example.writer.ExampleItemWriter;

import lombok.RequiredArgsConstructor;

/** 하나의 Job은 하나 이상의 Step으로 구성된다. Chunk 기반 Step의 기본 구조를 보여주는 예시이다. */
@Configuration
@RequiredArgsConstructor
public class ExampleStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ExampleItemReader reader;
    private final ExampleItemProcessor processor;
    private final ExampleItemWriter writer;

    @Bean
    public Step exampleStep() {
        return new StepBuilder("example-step", jobRepository)
                .<String, String>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
