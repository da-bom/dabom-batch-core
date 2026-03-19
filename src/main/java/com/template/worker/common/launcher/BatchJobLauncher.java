package com.template.worker.common.launcher;

import java.util.Collections;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** - 운영 시스템은 이 클래스를 통해 Job을 실행한다. - jobRegistry에 등록된 Job 이름만 전달하면 실행 가능하다. */
@Component
public class BatchJobLauncher {
    private static final String PARAM_LAUNCH_TIME = "launchTime";

    private final JobLauncher jobLauncher;
    private final JobLauncher manualAsyncJobLauncher;
    private final JobRegistry jobRegistry;

    public BatchJobLauncher(
            @Qualifier("jobLauncher") JobLauncher jobLauncher,
            @Qualifier("manualAsyncJobLauncher") JobLauncher manualAsyncJobLauncher,
            JobRegistry jobRegistry) {
        this.jobLauncher = jobLauncher;
        this.manualAsyncJobLauncher = manualAsyncJobLauncher;
        this.jobRegistry = jobRegistry;
    }

    public JobExecution run(String jobName, Map<String, String> params) throws Exception {
        return launch(jobName, params, jobLauncher);
    }

    public JobExecution runAsync(String jobName, Map<String, String> params) throws Exception {
        return launch(jobName, params, manualAsyncJobLauncher);
    }

    private JobExecution launch(String jobName, Map<String, String> params, JobLauncher launcher)
            throws Exception {
        Job job = jobRegistry.getJob(jobName);
        JobParametersBuilder builder = new JobParametersBuilder();
        Map<String, String> launchParams = (params == null) ? Collections.emptyMap() : params;
        launchParams.forEach(builder::addString);
        if (!launchParams.containsKey(PARAM_LAUNCH_TIME)) {
            builder.addLong(PARAM_LAUNCH_TIME, System.currentTimeMillis());
        }
        return launcher.run(job, builder.toJobParameters());
    }
}
