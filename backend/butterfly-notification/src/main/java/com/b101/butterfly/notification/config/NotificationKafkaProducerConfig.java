package com.b101.butterfly.notification.config;

import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import com.b101.butterfly.notification.messaging.NotificationSendResultPayload;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * notification 모듈의 Kafka Producer 설정.
 */
@Configuration
public class NotificationKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, CampaignExecutionResult> campaignExecutionResultProducerFactory(
            KafkaProperties properties
    ) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
    }

    @Bean
    public KafkaTemplate<String, CampaignExecutionResult> campaignExecutionResultKafkaTemplate(
            ProducerFactory<String, CampaignExecutionResult> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, NotificationSendResultPayload> notificationSendResultProducerFactory(
            KafkaProperties properties
    ) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
    }

    @Bean
    public KafkaTemplate<String, NotificationSendResultPayload> notificationSendResultKafkaTemplate(
            ProducerFactory<String, NotificationSendResultPayload> notificationSendResultProducerFactory
    ) {
        return new KafkaTemplate<>(notificationSendResultProducerFactory);
    }
}
