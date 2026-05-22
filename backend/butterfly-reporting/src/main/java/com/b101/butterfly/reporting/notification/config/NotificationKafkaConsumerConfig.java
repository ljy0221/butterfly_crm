package com.b101.butterfly.reporting.notification.config;

import com.b101.butterfly.reporting.notification.messaging.NotificationSendResultPayload;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * butterfly-worker 프로세스에서만 활성화되는 notification 도메인 Kafka consumer 설정.
 *
 * <p>EventKafkaConsumerConfig 패턴을 그대로 따른다 — 토픽별 독립 containerFactory,
 * AckMode.RECORD로 수동 커밋.
 *
 * <p><b>S14P31B101-180</b>: DLQ + 재시도 정책 추가.
 * <ul>
 *     <li>역직렬화 실패 → 즉시 DLQ ({@link ErrorHandlingDeserializer} +
 *         {@link DefaultErrorHandler#addNotRetryableExceptions} 분류)</li>
 *     <li>DB 일시 장애 → 지수 백오프 3회 재시도 후 DLQ</li>
 *     <li>{@link DataIntegrityViolationException}(UNIQUE 중복) → 재시도하지 않음
 *         (멱등 hit이므로 DLQ도 아님 — 컨슈머에서 catch하여 무시)</li>
 * </ul>
 */
@AutoConfiguration
@Profile("worker")
@EnableKafka
public class NotificationKafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, NotificationSendResultPayload> notificationSendResultConsumerFactory(
            KafkaProperties properties) {
        Map<String, Object> props = new HashMap<>(properties.buildConsumerProperties(null));
        // 토픽별 분리 — 글로벌 default type(EventPayload)이 본 컨슈머 페이로드를 덮어쓰지 못하게 한다.
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                NotificationSendResultPayload.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.b101.butterfly.reporting.notification.messaging");
        // 역직렬화 실패를 컨슈머 루프 밖으로 빼지 않고 ErrorHandler가 받아낼 수 있게 한다.
        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(
                        new org.apache.kafka.common.serialization.StringDeserializer()),
                new ErrorHandlingDeserializer<>(
                        new JsonDeserializer<>(NotificationSendResultPayload.class, false)));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationSendResultPayload>
    notificationSendResultListenerContainerFactory(
            ConsumerFactory<String, NotificationSendResultPayload> notificationSendResultConsumerFactory,
            DefaultErrorHandler notificationSendResultErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, NotificationSendResultPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationSendResultConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(notificationSendResultErrorHandler);
        return factory;
    }

    /**
     * DLQ 발행 + 재시도용 producer factory.
     *
     * <p>{@link DeadLetterPublishingRecoverer}는 원본 record의 key/value를 그대로 넘기는데,
     * 역직렬화 실패 케이스(byte[])와 정상 케이스(String key + JSON value)가 둘 다 들어올 수
     * 있다. {@link DelegatingByTypeSerializer}로 타입별 분기 — byte[]는 {@link ByteArraySerializer},
     * 그 외(String/객체)는 {@link StringSerializer}/{@link org.springframework.kafka.support.serializer.JsonSerializer}.
     */
    @Bean
    public ProducerFactory<Object, Object> notificationDlqProducerFactory(KafkaProperties properties) {
        Map<String, Object> props = new HashMap<>(properties.buildProducerProperties(null));
        // serializer 클래스 키는 명시적으로 지우고 instance를 직접 넘긴다 — DelegatingByTypeSerializer는
        // 클래스 이름으로 reflective 인스턴스화가 안 되기 때문.
        props.remove(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
        props.remove(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        Map<Class<?>, Serializer<?>> keyDelegates = new HashMap<>();
        keyDelegates.put(byte[].class, new ByteArraySerializer());
        keyDelegates.put(String.class, new StringSerializer());

        Map<Class<?>, Serializer<?>> valueDelegates = new HashMap<>();
        valueDelegates.put(byte[].class, new ByteArraySerializer());
        valueDelegates.put(String.class, new StringSerializer());

        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(props);
        factory.setKeySerializer(new DelegatingByTypeSerializer(keyDelegates, true));
        factory.setValueSerializer(new DelegatingByTypeSerializer(valueDelegates, true));
        return factory;
    }

    @Bean
    public KafkaTemplate<Object, Object> notificationDlqKafkaTemplate(
            ProducerFactory<Object, Object> notificationDlqProducerFactory) {
        return new KafkaTemplate<>(notificationDlqProducerFactory);
    }

    /**
     * 지수 백오프 3회 재시도 후 DLQ로 발행하는 ErrorHandler.
     *
     * <p>재시도 가능: DB 일시 장애 등 일반 RuntimeException.
     * 재시도 불가(즉시 DLQ): 역직렬화 실패, NPE 같이 재시도해도 결과가 같은 예외.
     * 재시도/DLQ 모두 스킵: {@link DataIntegrityViolationException} — 멱등 hit이므로 정상.
     */
    @Bean
    public DefaultErrorHandler notificationSendResultErrorHandler(
            KafkaTemplate<Object, Object> notificationDlqKafkaTemplate,
            @Value("${butterfly.kafka.notification.dlq-suffix:-dlq}") String dlqSuffix
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                notificationDlqKafkaTemplate,
                (record, exception) ->
                        new org.apache.kafka.common.TopicPartition(
                                record.topic() + dlqSuffix, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(10_000L);
        // 초기 시도 1회 + 재시도 3회 = 총 4회 attempt 후 DLQ.
        backOff.setMaxElapsedTime(20_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // 즉시 DLQ — 재시도해도 같은 결과.
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class,
                NullPointerException.class
        );
        // 멱등 hit — 정상 흐름이므로 재시도/DLQ 모두 건너뛰기 위해
        // 컨슈머 레이어에서 swallow한다 (NotificationSendResultConsumer 참고).
        return handler;
    }
}
