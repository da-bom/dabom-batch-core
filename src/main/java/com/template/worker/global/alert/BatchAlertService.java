package com.template.worker.global.alert;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BatchAlertService {

    // Slack Incoming Webhook으로 배치 실패 알람을 전송하는 공통 컴포넌트
    private static final String UNKNOWN_VALUE = "unknown";
    private static final String JOB_FAILURE_TITLE = "*DABOM Batch 실패*";
    private static final String SCHEDULER_FAILURE_TITLE = "*DABOM Batch 스케줄러 실패*";

    private final RestTemplate restTemplate;
    private final String webhookUrl;

    @Autowired
    public BatchAlertService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${slack.webhook.url:}") String webhookUrl) {
        this(restTemplateBuilder.build(), webhookUrl);
    }

    BatchAlertService(RestTemplate restTemplate, String webhookUrl) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
    }

    public void sendJobFailureAlert(
            String jobName,
            Long jobExecutionId,
            String parameterSummary,
            String errorSummary,
            String technicalDetail) {
        // 운영자가 한눈에 보도록 요약/원본 예외를 멀티라인 메시지로 구성
        sendAlert(
                buildMultilineMessage(
                        JOB_FAILURE_TITLE,
                        "잡",
                        sanitize(jobName),
                        "실행 ID",
                        jobExecutionId == null ? UNKNOWN_VALUE : jobExecutionId.toString(),
                        "파라미터",
                        sanitize(parameterSummary),
                        "상태",
                        "FAILED",
                        "원인 요약",
                        sanitize(errorSummary),
                        "원본 예외",
                        sanitize(technicalDetail)));
    }

    public void sendSchedulerFailureAlert(
            String schedulerName, String parameterSummary, String errorMessage) {
        // 스케줄러 launch 단계 예외는 Job FAILED 알람과 구분해 별도 제목으로 보냄
        sendAlert(
                buildMultilineMessage(
                        SCHEDULER_FAILURE_TITLE,
                        "스케줄러",
                        sanitize(schedulerName),
                        "파라미터",
                        sanitize(parameterSummary),
                        "원인",
                        sanitize(errorMessage)));
    }

    private void sendAlert(String message) {
        if (!StringUtils.hasText(webhookUrl)) {
            // 알람 설정이 비어 있어도 원래 배치 실패 흐름은 깨지지 않게 함
            log.warn(
                    "Skip batch alert because slack webhook url is not configured. message={}",
                    message);
            return;
        }

        try {
            ResponseEntity<Void> response =
                    restTemplate.postForEntity(
                            webhookUrl, new SlackWebhookRequest(message), Void.class);
            log.info(
                    "Batch alert sent to Slack. statusCode={}, message={}",
                    response.getStatusCode(),
                    message);
        } catch (RestClientException exception) {
            log.error("Failed to send batch alert to Slack. message={}", message, exception);
        }
    }

    private String sanitize(String value) {
        // Slack 한 줄 필드가 깨지지 않도록 줄바꿈과 빈 문자열 정리
        if (!StringUtils.hasText(value)) {
            return UNKNOWN_VALUE;
        }
        return value.replace("\r\n", " ").replace("\n", " ").trim();
    }

    private String buildMultilineMessage(String title, String... keyValues) {
        // text payload만으로도 읽기 좋게 보이도록 bullet 형식으로 렌더링
        List<String> lines = new ArrayList<>();
        lines.add(title);

        for (int index = 0; index < keyValues.length; index += 2) {
            String key = keyValues[index];
            String value = keyValues[index + 1];
            lines.add(String.format("• %s: %s", key, value));
        }

        return String.join("\n", lines);
    }

    private record SlackWebhookRequest(String text) {}
}
