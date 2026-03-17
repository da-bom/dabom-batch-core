package com.template.worker.common.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
class BatchJobLauncherTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private JobRegistry jobRegistry;
    @Mock private Job job;
    @Mock private JobExecution jobExecution;

    @InjectMocks private BatchJobLauncher batchJobLauncher;

    @Test
    @DisplayName("run - launchTime 파라미터를 자동으로 추가한다")
    void run_addsLaunchTimeParam() throws Exception {
        // given
        when(jobRegistry.getJob("example-job")).thenReturn(job);
        when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(jobExecution);

        // when
        batchJobLauncher.run("example-job", Map.of("targetMonth", "2026-03-01"));

        // then
        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(job), captor.capture());
        JobParameters parameters = captor.getValue();
        assertThat(parameters.getString("targetMonth")).isEqualTo("2026-03-01");
        assertThat(parameters.getLong("launchTime")).isNotNull();
    }

    @Test
    @DisplayName("run - params가 null이어도 실행한다")
    void run_acceptsNullParams() throws Exception {
        // given
        when(jobRegistry.getJob("example-job")).thenReturn(job);
        when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(jobExecution);

        // when
        batchJobLauncher.run("example-job", null);

        // then
        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(job), captor.capture());
        assertThat(captor.getValue().getLong("launchTime")).isNotNull();
    }
}
