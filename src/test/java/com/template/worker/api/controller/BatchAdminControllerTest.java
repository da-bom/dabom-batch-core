package com.template.worker.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.template.worker.api.dto.RunBatchRequest;
import com.template.worker.api.service.BatchAdminService;

@WebMvcTest(BatchAdminController.class)
class BatchAdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BatchAdminService batchAdminService;

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

        doNothing().when(batchAdminService).run(any(RunBatchRequest.class));

        // when & then
        mockMvc.perform(
                        post("/batch/run")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isOk());

        verify(batchAdminService).run(any(RunBatchRequest.class));
    }
}
