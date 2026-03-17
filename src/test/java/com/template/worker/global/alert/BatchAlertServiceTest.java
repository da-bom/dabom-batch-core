package com.template.worker.global.alert;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class BatchAlertServiceTest {

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/test";

    @Test
    @DisplayName("sendJobFailureAlert - webhook 이 있으면 Slack payload를 POST 한다")
    void sendJobFailureAlert_postsSlackPayloadWhenWebhookConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        BatchAlertService batchAlertService = new BatchAlertService(restTemplate, WEBHOOK_URL);

        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"text\":")))
                .andExpect(content().string(containsString("*DABOM Batch 실패*")))
                .andExpect(content().string(containsString("• 잡: monthly-usage-reset-job")))
                .andExpect(content().string(containsString("• 파라미터: targetMonth=2026-03-01")))
                .andExpect(content().string(containsString("• 원인 요약: Redis 연결 실패로 재시도 3회 후 최종 실패")))
                .andExpect(
                        content()
                                .string(containsString("• 원본 예외: RedisConnectionFailureException")))
                .andRespond(withSuccess());

        batchAlertService.sendJobFailureAlert(
                "monthly-usage-reset-job",
                10L,
                "targetMonth=2026-03-01",
                "Redis 연결 실패로 재시도 3회 후 최종 실패",
                "RedisConnectionFailureException: Unable to connect to Redis");

        server.verify();
    }

    @Test
    @DisplayName("sendJobFailureAlert - webhook 이 비어 있으면 예외 없이 건너뛴다")
    void sendJobFailureAlert_skipsWhenWebhookIsBlank() {
        BatchAlertService batchAlertService = new BatchAlertService(new RestTemplate(), " ");

        assertThatCode(
                        () ->
                                batchAlertService.sendJobFailureAlert(
                                        "monthly-usage-reset-job",
                                        10L,
                                        "targetMonth=2026-03-01",
                                        "Redis 연결 실패",
                                        "RedisConnectionFailureException: boom"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendSchedulerFailureAlert - Slack 호출 실패를 삼키고 로그만 남긴다")
    void sendSchedulerFailureAlert_swallowsSlackException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BatchAlertService batchAlertService = new BatchAlertService(restTemplate, WEBHOOK_URL);
        doThrow(new RestClientException("failed") {})
                .when(restTemplate)
                .postForEntity(eq(WEBHOOK_URL), any(), eq(Void.class));

        assertThatCode(
                        () ->
                                batchAlertService.sendSchedulerFailureAlert(
                                        "weekly-family-recap-scheduler",
                                        "weekStartDate=2026-03-09",
                                        "failed"))
                .doesNotThrowAnyException();

        verify(restTemplate).postForEntity(eq(WEBHOOK_URL), any(), eq(Void.class));
    }
}
