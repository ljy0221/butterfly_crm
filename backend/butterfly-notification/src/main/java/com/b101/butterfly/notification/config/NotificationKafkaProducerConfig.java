package com.b101.butterfly.notification.config;

import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * notification 모듈의 Kafka Producer 설정.
 *
 * <p>현재는 {@link CampaignExecutionResult} 발행용 한 종(campaign-execution-result 토픽)만 정의.
 * worker 의 {@code WorkerKafkaConfig} 와 동일 패턴 — {@link KafkaProperties#buildProducerProperties}
 * 로 application 측 spring.kafka.producer.* 설정을 읽어 ProducerFactory 구성.
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
}
