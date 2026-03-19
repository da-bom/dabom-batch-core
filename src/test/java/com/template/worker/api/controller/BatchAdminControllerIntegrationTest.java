package com.template.worker.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.worker.WorkerApplication;
import com.template.worker.api.dto.BatchJobExecutionResponse;
import com.template.worker.api.dto.RunBatchResponse;

@SpringBootTest(classes = WorkerApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BatchAdminControllerIntegrationTest.TestBatchJobConfig.class)
class BatchAdminControllerIntegrationTest {
    private static final String TEST_JOB_NAME = "test-manual-async-job";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("수동 실행 API는 비동기로 시작하고 상태 조회 API에서 완료를 확인할 수 있다")
    void runAndGetJobExecution() throws Exception {
        String requestJson =
                """
                {
                  "jobName": "test-manual-async-job",
                  "params": {}
                }
                """;

        MvcResult startResult =
                mockMvc.perform(
                                post("/batch/run")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestJson))
                        .andExpect(status().isAccepted())
                        .andReturn();

        RunBatchResponse runBatchResponse =
                objectMapper.readValue(
                        startResult.getResponse().getContentAsString(), RunBatchResponse.class);

        assertThat(runBatchResponse.jobName()).isEqualTo(TEST_JOB_NAME);
        assertThat(runBatchResponse.status()).isIn("STARTING", "STARTED");
        assertThat(runBatchResponse.jobExecutionId()).isNotNull();

        Long jobExecutionId = runBatchResponse.jobExecutionId();

        BatchJobExecutionResponse pollingResponse = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult pollResult =
                    mockMvc.perform(get("/batch/jobs/{jobExecutionId}", jobExecutionId))
                            .andExpect(status().isOk())
                            .andReturn();

            pollingResponse =
                    objectMapper.readValue(
                            pollResult.getResponse().getContentAsString(),
                            BatchJobExecutionResponse.class);

            if ("COMPLETED".equals(pollingResponse.status())) {
                break;
            }
        }

        assertThat(pollingResponse).isNotNull();
        assertThat(pollingResponse.jobExecutionId()).isEqualTo(jobExecutionId);
        assertThat(pollingResponse.jobName()).isEqualTo(TEST_JOB_NAME);
        assertThat(pollingResponse.status()).isEqualTo("COMPLETED");
        assertThat(pollingResponse.exitCode()).isEqualTo("COMPLETED");
        assertThat(pollingResponse.endTime()).isNotNull();
    }

    @TestConfiguration
    static class TestBatchJobConfig {

        @Bean
        Job testManualAsyncJob(JobRepository jobRepository, Step testManualAsyncStep) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository).start(testManualAsyncStep).build();
        }

        @Bean
        Step testManualAsyncStep(
                JobRepository jobRepository, DataSourceTransactionManager transactionManager) {
            return new StepBuilder("test-manual-async-step", jobRepository)
                    .tasklet(
                            (contribution, chunkContext) -> {
                                return RepeatStatus.FINISHED;
                            },
                            transactionManager)
                    .build();
        }
    }
}
