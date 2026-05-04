package com.b101.butterfly.segment.config;

import com.b101.butterfly.segment.messaging.SegmentRebuildMessage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * butterfly-api 프로세스에서만 활성화되는 segment 도메인의 Kafka producer 설정.
 *
 * <p>event/notification 모듈의 producer 설정 패턴 미러. {@code @Profile("api")}로 worker 부팅 시
 * 빈 등록 회피.
 */
@AutoConfiguration
@Profile("api")
public class SegmentKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, SegmentRebuildMessage> segmentRebuildProducerFactory(
            KafkaProperties properties) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
    }

    @Bean
    public KafkaTemplate<String, SegmentRebuildMessage> segmentRebuildKafkaTemplate(
            ProducerFactory<String, SegmentRebuildMessage> segmentRebuildProducerFactory) {
        return new KafkaTemplate<>(segmentRebuildProducerFactory);
    }
}
