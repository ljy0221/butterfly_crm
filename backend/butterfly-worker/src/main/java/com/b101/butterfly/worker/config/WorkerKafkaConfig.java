package com.b101.butterfly.worker.config;

import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import java.util.Map;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Worker 프로세스의 Kafka Consumer/Producer 설정.
 *
 * <p>Consumer: {@code campaign.send.requested} 토픽 (CampaignSendInstruction)
 * <p>Producer: {@code delivery-requests} 토픽 (DeliveryCommand)
 */
@Configuration
@Lazy(false)
@Profile("worker")
public class WorkerKafkaConfig {

    /**
     * CampaignSendInstruction 메시지 Consumer Factory.
     */
    @Bean
    public ConsumerFactory<String, CampaignSendInstruction> campaignSendInstructionConsumerFactory(
            KafkaProperties properties
    ) {
        Map<String, Object> props = new java.util.HashMap<>(properties.buildConsumerProperties(null));
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CampaignSendInstruction.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.b101.butterfly.*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(CampaignSendInstruction.class, false));
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
     * DeliveryCommand 메시지 Consumer Factory.
     * DeliveryKafkaConsumer 가 containerFactory = "kafkaListenerContainerFactory" (default 이름) 를 사용.
     */
    @Bean
    public ConsumerFactory<String, DeliveryCommand> deliveryCommandConsumerFactory(
            KafkaProperties properties
    ) {
        Map<String, Object> props = new java.util.HashMap<>(properties.buildConsumerProperties(null));
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DeliveryCommand.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.b101.butterfly.*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(DeliveryCommand.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeliveryCommand>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, DeliveryCommand> deliveryCommandConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, DeliveryCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(deliveryCommandConsumerFactory);
        return factory;
    }

    /**
     * CampaignExecutionResult 메시지 Consumer Factory — notification 측 publisher 가 보낸
     * per-execution summary 를 받아 SEND → SUCCESS|FAIL 전이를 수행한다 (S14P31B101-284).
     */
    @Bean
    public ConsumerFactory<String, CampaignExecutionResult> campaignExecutionResultConsumerFactory(
            KafkaProperties properties
    ) {
        Map<String, Object> props = new java.util.HashMap<>(properties.buildConsumerProperties(null));
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CampaignExecutionResult.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.b101.butterfly.*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(CampaignExecutionResult.class, false));
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
