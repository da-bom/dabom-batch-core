package com.template.worker.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.template.worker.api.dto.BatchJobExecutionResponse;
import com.template.worker.api.dto.RunBatchRequest;
import com.template.worker.api.dto.RunBatchResponse;
import com.template.worker.api.service.BatchAdminService;
import com.template.worker.api.service.BatchJobQueryService;

@WebMvcTest(
        value = BatchAdminController.class,
        properties = "spring.mvc.problemdetails.enabled=true")
class BatchAdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BatchAdminService batchAdminService;
    @MockitoBean private BatchJobQueryService batchJobQueryService;

    @Test
    @DisplayName("POST /batch/run - 배치 실행 요청 성공")
    void runBatch_success() throws Exception {
        // given
        String requestJson =
                """
                {
                  "jobName": "exampleJob",
                  "params": {"date": "2024-01-01"}
                }
                """;

        when(batchAdminService.run(any(RunBatchRequest.class)))
                .thenReturn(
                        new RunBatchResponse(1L, "exampleJob", "STARTING", "Batch job accepted"));

        // when & then
        mockMvc.perform(
                        post("/batch/run")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(1L))
                .andExpect(jsonPath("$.jobName").value("exampleJob"))
                .andExpect(jsonPath("$.status").value("STARTING"))
                .andExpect(jsonPath("$.message").value("Batch job accepted"));

        verify(batchAdminService).run(any(RunBatchRequest.class));
    }

    @Test
    @DisplayName("GET /batch/jobs/{jobExecutionId} - 배치 실행 상태 조회 성공")
    void getJobExecution_success() throws Exception {
        when(batchJobQueryService.getJobExecution(1L))
                .thenReturn(
                        new BatchJobExecutionResponse(
                                1L,
                                "exampleJob",
                                "COMPLETED",
                                null,
                                null,
                                null,
                                "COMPLETED",
                                null));

        mockMvc.perform(get("/batch/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(1L))
                .andExpect(jsonPath("$.jobName").value("exampleJob"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /batch/jobs/{jobExecutionId} - 없는 실행 ID면 404")
    void getJobExecution_notFound() throws Exception {
        when(batchJobQueryService.getJobExecution(999L))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Job execution not found: 999"));

        mockMvc.perform(get("/batch/jobs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Job execution not found: 999"));
    }
}
