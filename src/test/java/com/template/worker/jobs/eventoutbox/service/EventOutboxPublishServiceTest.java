package com.template.worker.jobs.eventoutbox.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dabom.messaging.kafka.error.KafkaMessageProcessingException;
import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.dabom.messaging.kafka.event.dto.notification.NotificationType;
import com.template.worker.jobs.eventoutbox.model.EventOutboxRow;
import com.template.worker.jobs.eventoutbox.query.EventOutboxQueryRepository;
import com.template.worker.jobs.eventoutbox.support.EventOutboxProperties;
import com.template.worker.jobs.eventoutbox.support.EventOutboxRetryPolicy;

@ExtendWith(MockitoExtension.class)
class EventOutboxPublishServiceTest {

    @Mock private EventOutboxQueryRepository repository;
    @Mock private EventOutboxPayloadMapper payloadMapper;
    @Mock private EventOutboxKafkaPublisher notificationPublisher;
    @Mock private EventOutboxMetrics metrics;

    private EventOutboxProperties properties;
    private EventOutboxRetryPolicy retryPolicy;
    private ExecutorService executorService;
    private EventOutboxPublishService service;

    @BeforeEach
    void setUp() {
        properties = new EventOutboxProperties();
        properties.setBatchSize(10);
        properties.setMaxRetry(5);
        properties.setRetryInitialDelay(Duration.ofMinutes(1));
        properties.setRetryMaxDelay(Duration.ofMinutes(16));

        retryPolicy = new EventOutboxRetryPolicy(properties);
        executorService = Executors.newSingleThreadExecutor();
        service =
                new EventOutboxPublishService(
                        repository,
                        payloadMapper,
                        notificationPublisher,
                        retryPolicy,
                        properties,
                        executorService,
                        metrics);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    @DisplayName("publishPendingOutboxes - 발행 성공 시 SENT로 갱신한다")
    void publishPendingOutboxes_marksSentOnSuccess() {
        EventOutboxRow row = new EventOutboxRow(1L, "evt_1", "{\"familyId\":10}", 0);
        NotificationPayload notificationPayload =
                new NotificationPayload(
                        10L,
                        1L,
                        NotificationType.CUSTOMER_BLOCKED,
                        "데이터 사용 차단",
                        "현재 앱 사용이 차단되어 있습니다.",
                        Map.of("originEventId", "evt_1"));

        when(repository.pollPublishableRows(anyInt(), anyInt(), any())).thenReturn(List.of(row));
        when(payloadMapper.readPayload(any())).thenReturn(notificationPayload);

        service.publishPendingOutboxes();

        verify(notificationPublisher).publish("evt_1", notificationPayload);
        verify(repository).markSent(anyLong(), any());
        verify(metrics).recordSuccess();
        verify(repository, never()).markPendingForRetry(anyLong(), anyInt(), any(), any(), any());
        verify(repository, never()).markFailed(anyLong(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("publishPendingOutboxes - retryable 실패면 PUBLISH_PENDING과 next_retry_at을 갱신한다")
    void publishPendingOutboxes_marksPendingForRetryOnRetryableError() {
        EventOutboxRow row = new EventOutboxRow(1L, "evt_1", "{\"familyId\":10}", 0);
        NotificationPayload notificationPayload =
                new NotificationPayload(
                        10L,
                        1L,
                        NotificationType.CUSTOMER_BLOCKED,
                        "데이터 사용 차단",
                        "현재 앱 사용이 차단되어 있습니다.",
                        Map.of("originEventId", "evt_1"));

        when(repository.pollPublishableRows(anyInt(), anyInt(), any())).thenReturn(List.of(row));
        when(payloadMapper.readPayload(any())).thenReturn(notificationPayload);
        doThrow(
                        new KafkaMessageProcessingException(
                                "publish failed",
                                new java.util.concurrent.TimeoutException("timeout")))
                .when(notificationPublisher)
                .publish(any(), any());

        service.publishPendingOutboxes();

        verify(repository).markPendingForRetry(anyLong(), anyInt(), any(), any(), any());
        verify(metrics).recordRetry(anyInt());
        verify(repository, never()).markFailed(anyLong(), anyInt(), any(), any());
        verify(repository, never()).markSent(anyLong(), any());
    }

    @Test
    @DisplayName("publishPendingOutboxes - non-retryable 실패면 FAILED로 갱신한다")
    void publishPendingOutboxes_marksFailedOnNonRetryableError() {
        EventOutboxRow row = new EventOutboxRow(1L, "evt_1", "{\"familyId\":10}", 0);

        when(repository.pollPublishableRows(anyInt(), anyInt(), any())).thenReturn(List.of(row));
        when(payloadMapper.readPayload(any()))
                .thenThrow(new IllegalArgumentException("bad payload"));

        service.publishPendingOutboxes();

        verify(repository).markFailed(anyLong(), anyInt(), any(), any());
        verify(metrics).recordFailure(anyInt());
        verify(repository, never()).markPendingForRetry(anyLong(), anyInt(), any(), any(), any());
        verify(repository, never()).markSent(anyLong(), any());
    }
}
