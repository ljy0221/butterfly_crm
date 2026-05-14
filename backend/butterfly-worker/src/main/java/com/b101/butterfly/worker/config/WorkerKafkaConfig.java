package com.b101.butterfly.worker.config;

import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Worker 프로세스의 Kafka Consumer/Producer 설정.
 *
 * <p>Consumer: {@code campaign.send.requested} 토픽 (CampaignSendInstruction)
 * <p>Producer: {@code delivery-requests} 토픽 (DeliveryCommand)
 *
 * <p>S14P31B101-294 (포트폴리오 측정 기반 개선) — KafkaAdmin Bean 으로 토픽 명시 생성.
 * Phase A baseline 은 partition=1 / replication=1 유지 (BEFORE 측정 대상).
 * Phase B 이후 가설 H1/H3 적용 시 본 Bean 의 partition 수 조정.
 */
@Configuration
@Profile("worker")
public class WorkerKafkaConfig {

    // S14P31B101-294 Phase B H1 가설 (Partition only) — partition 1 → 8, concurrency 유지.
    // 예상: 효과 0% — 1 thread 가 모든 partition consume.
    public static final int CAMPAIGN_SEND_REQUESTED_PARTITIONS_BASELINE = 8;
    public static final int CAMPAIGN_SEND_REQUESTED_REPLICAS = 1;     // local broker

    @Bean
    public NewTopic campaignSendRequestedTopic() {
        return TopicBuilder.name("campaign.send.requested")
                .partitions(CAMPAIGN_SEND_REQUESTED_PARTITIONS_BASELINE)
                .replicas(CAMPAIGN_SEND_REQUESTED_REPLICAS)
                .build();
    }

    /**
     * CampaignSendInstruction 메시지 Consumer Factory.
     */
    @Bean
    public ConsumerFactory<String, CampaignSendInstruction> campaignSendInstructionConsumerFactory(
            KafkaProperties properties
    ) {
        return new DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties(null));
    }

    /**
     * CampaignSendInstruction 메시지 Listener Container Factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CampaignSendInstruction>
    campaignSendInstructionListenerContainerFactory(
            ConsumerFactory<String, CampaignSendInstruction> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CampaignSendInstruction> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // TODO: 동시성, 재시도, 에러 핸들링 설정
        return factory;
    }

    /**
     * DeliveryCommand 메시지 Producer Factory.
     */
    @Bean
    public ProducerFactory<String, DeliveryCommand> deliveryCommandProducerFactory(
            KafkaProperties properties
    ) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
    }

    /**
     * DeliveryCommand 메시지 KafkaTemplate.
     */
    @Bean
    public KafkaTemplate<String, DeliveryCommand> deliveryCommandKafkaTemplate(
            ProducerFactory<String, DeliveryCommand> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * CampaignExecutionResult 메시지 Consumer Factory — notification 측 publisher 가 보낸
     * per-execution summary 를 받아 SEND → SUCCESS|FAIL 전이를 수행한다 (S14P31B101-284).
     */
    @Bean
    public ConsumerFactory<String, CampaignExecutionResult> campaignExecutionResultConsumerFactory(
            KafkaProperties properties
    ) {
        return new DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties(null));
    }

    /**
     * CampaignExecutionResult 메시지 Listener Container Factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CampaignExecutionResult>
    campaignExecutionResultListenerContainerFactory(
            ConsumerFactory<String, CampaignExecutionResult> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CampaignExecutionResult> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
