package com.b101.butterfly.campaign.lifecycle.infrastructure.kafka;

import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * butterfly-api 프로세스에서만 활성화되는 캠페인 발송 지시(Send Instruction) Kafka producer 설정.
 *
 * <p>ADR-0011 패턴 준수 — producer는 api에서만 활성화되며, 토픽은 {@code campaign.send.requested}.
 * Worker가 이 토픽을 consume하여 실제 발송 fan-out 책임.
 */
@AutoConfiguration
@Profile("api")
public class CampaignKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, CampaignSendInstruction> campaignSendInstructionProducerFactory(
            KafkaProperties properties
    ) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
    }

    @Bean
    public KafkaTemplate<String, CampaignSendInstruction> campaignSendInstructionKafkaTemplate(
            ProducerFactory<String, CampaignSendInstruction> campaignSendInstructionProducerFactory
    ) {
        return new KafkaTemplate<>(campaignSendInstructionProducerFactory);
    }
}