package com.template.worker.global.launcher;

import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** - 운영 시스템은 이 클래스를 통해 Job을 실행한다. - jobRegistry에 등록된 Job 이름만 전달하면 실행 가능하다. */
@Component
@RequiredArgsConstructor
public class BatchJobLauncher {
    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;

    public void run(String jobName, Map<String, String> params) throws Exception {
        Job job = jobRegistry.getJob(jobName);
        JobParametersBuilder builder = new JobParametersBuilder();
        params.forEach(builder::addString);
        jobLauncher.run(job, builder.toJobParameters());
    }
}
