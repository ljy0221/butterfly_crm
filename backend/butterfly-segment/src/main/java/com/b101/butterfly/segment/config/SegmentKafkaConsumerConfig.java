package com.b101.butterfly.segment.config;

import com.b101.butterfly.segment.messaging.SegmentRebuildMessage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * butterfly-worker 프로세스에서만 활성화되는 segment 도메인의 Kafka consumer 설정.
 *
 * <p>{@code SegmentRebuildMessage} 한정 ConsumerFactory + ListenerContainerFactory를 별도로
 * 등록한다 (이씨 [-21]의 event 모듈 패턴 미러). worker `application.properties`의 default
 * `JsonDeserializer` 설정은 {@code com.b101.butterfly.event.messaging.EventPayload}로 박혀있어
 * 충돌하지 않도록 본 factory는 자체 deserializer를 구성한다.
 */
@AutoConfiguration
@Profile("worker")
@EnableKafka
public class SegmentKafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, SegmentRebuildMessage> segmentRebuildConsumerFactory(
            KafkaProperties properties) {
        Map<String, Object> props = new HashMap<>(properties.buildConsumerProperties(null));
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.b101.butterfly.segment.messaging");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SegmentRebuildMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SegmentRebuildMessage>
            segmentRebuildKafkaListenerContainerFactory(
                    ConsumerFactory<String, SegmentRebuildMessage>
                            segmentRebuildConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, SegmentRebuildMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(segmentRebuildConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
