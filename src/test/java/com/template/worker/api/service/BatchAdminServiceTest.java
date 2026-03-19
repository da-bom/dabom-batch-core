package com.template.worker.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;

import com.template.worker.api.dto.RunBatchRequest;
import com.template.worker.api.dto.RunBatchResponse;
import com.template.worker.common.launcher.BatchJobLauncher;

@ExtendWith(MockitoExtension.class)
class BatchAdminServiceTest {

    @Mock private BatchJobLauncher launcher;

    @InjectMocks private BatchAdminService batchAdminService;

    @Test
    @DisplayName("run - BatchJobLauncher에 위임하여 배치 실행")
    void run_delegatesToLauncher() throws Exception {
        // given
        RunBatchRequest request = createRequest("exampleJob", Map.of("date", "2024-01-01"));
        JobExecution jobExecution = new JobExecution(1L);
        jobExecution.setJobInstance(new JobInstance(1L, "exampleJob"));
        jobExecution.setStatus(BatchStatus.STARTING);
        when(launcher.runAsync(eq("exampleJob"), anyMap())).thenReturn(jobExecution);

        // when
        RunBatchResponse response = batchAdminService.run(request);

        // then
        verify(launcher).runAsync(eq("exampleJob"), anyMap());
        assertThat(response.jobExecutionId()).isEqualTo(1L);
        assertThat(response.jobName()).isEqualTo("exampleJob");
        assertThat(response.status()).isEqualTo("STARTING");
        assertThat(response.message()).isEqualTo("Batch job accepted");
    }

    private RunBatchRequest createRequest(String jobName, Map<String, String> params)
            throws Exception {
        RunBatchRequest request = new RunBatchRequest();
        setField(request, "jobName", jobName);
        setField(request, "params", params);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
