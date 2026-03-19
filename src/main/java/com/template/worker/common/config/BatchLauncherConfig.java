package com.template.worker.common.config;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BatchLauncherConfig {

    @Bean(name = "manualBatchTaskExecutor")
    public TaskExecutor manualBatchTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(2);
        taskExecutor.setMaxPoolSize(4);
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("manual-batch-");
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.initialize();
        return taskExecutor;
    }

    @Bean(name = "manualAsyncJobLauncher")
    public JobLauncher manualAsyncJobLauncher(
            JobRepository jobRepository,
            @Qualifier("manualBatchTaskExecutor") TaskExecutor taskExecutor)
            throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(taskExecutor);
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }
}
