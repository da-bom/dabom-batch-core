package com.template.worker.jobs.eventoutbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dabom.messaging.kafka.error.NonRetryableKafkaMessageProcessingException;
import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.dabom.messaging.kafka.event.dto.notification.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;

class EventOutboxPayloadMapperTest {

    private final EventOutboxPayloadMapper mapper =
            new EventOutboxPayloadMapper(new ObjectMapper().findAndRegisterModules());

    @Test
    @DisplayName("readPayload - NotificationPayload JSON을 역직렬화한다")
    void readPayload_parsesValidPayload() {
        String json =
                """
                {
                  "familyId": 11,
                  "customerId": 11,
                  "type": "CUSTOMER_BLOCKED",
                  "title": "데이터 사용 차단",
                  "message": "현재 앱 사용이 차단되어 있습니다. 대상 앱: com.netflix.app",
                  "data": {
                    "familyId": 11,
                    "customerId": 11,
                    "originEventId": "evt_123"
                  }
                }
                """;

        NotificationPayload payload = mapper.readPayload(json);

        assertThat(payload.familyId()).isEqualTo(11L);
        assertThat(payload.customerId()).isEqualTo(11L);
        assertThat(payload.type()).isEqualTo(NotificationType.CUSTOMER_BLOCKED);
        assertThat(payload.data()).containsEntry("originEventId", "evt_123");
    }

    @Test
    @DisplayName("readPayload - 필수 필드가 비면 예외가 발생한다")
    void readPayload_rejectsMissingRequiredFields() {
        String json =
                """
                {
                  "familyId": 11,
                  "customerId": 11,
                  "type": "CUSTOMER_BLOCKED",
                  "title": " ",
                  "message": "현재 앱 사용이 차단되어 있습니다."
                }
                """;

        assertThatThrownBy(() -> mapper.readPayload(json))
                .isInstanceOf(NonRetryableKafkaMessageProcessingException.class)
                .hasMessage("Outbox payload title must not be blank.");
    }
}
