package com.template.worker.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.web.server.ResponseStatusException;

import com.template.worker.api.dto.BatchJobExecutionResponse;

@ExtendWith(MockitoExtension.class)
class BatchJobQueryServiceTest {

    @Mock private JobExplorer jobExplorer;

    @InjectMocks private BatchJobQueryService batchJobQueryService;

    @Test
    @DisplayName("getJobExecution - 실행 정보를 응답 DTO로 변환한다")
    void getJobExecution_mapsExecution() {
        JobExecution jobExecution = new JobExecution(1L);
        jobExecution.setJobInstance(new JobInstance(1L, "example-job"));
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setCreateTime(LocalDateTime.of(2026, 3, 19, 13, 0));
        jobExecution.setStartTime(LocalDateTime.of(2026, 3, 19, 13, 0, 1));
        jobExecution.setEndTime(LocalDateTime.of(2026, 3, 19, 13, 0, 2));
        jobExecution.setExitStatus(new ExitStatus("COMPLETED", "done"));
        when(jobExplorer.getJobExecution(1L)).thenReturn(jobExecution);

        BatchJobExecutionResponse response = batchJobQueryService.getJobExecution(1L);

        assertThat(response.jobExecutionId()).isEqualTo(1L);
        assertThat(response.jobName()).isEqualTo("example-job");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.exitCode()).isEqualTo("COMPLETED");
        assertThat(response.exitDescription()).isEqualTo("done");
    }

    @Test
    @DisplayName("getJobExecution - 존재하지 않으면 404 예외를 던진다")
    void getJobExecution_notFound() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);

        assertThatThrownBy(() -> batchJobQueryService.getJobExecution(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND")
                .hasMessageContaining("Job execution not found: 999");
    }
}
