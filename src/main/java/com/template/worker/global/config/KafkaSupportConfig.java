package com.template.worker.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dabom.messaging.kafka.event.KafkaEventMessageSupport;
import com.dabom.messaging.kafka.support.KafkaLogSanitizer;

@Configuration
public class KafkaSupportConfig {

    @Bean
    public KafkaLogSanitizer kafkaLogSanitizer() {
        return new KafkaLogSanitizer();
    }

    @Bean
    public KafkaEventMessageSupport kafkaEventMessageSupport(
            ObjectMapper objectMapper, KafkaLogSanitizer kafkaLogSanitizer) {
        return new KafkaEventMessageSupport(objectMapper, kafkaLogSanitizer);
    }
}
