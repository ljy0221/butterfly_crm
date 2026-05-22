package com.b101.butterfly.reporting.notification.messaging;

import com.b101.butterfly.reporting.ReportingTestApplication;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * S14P31B101-180 — DLQ + 재시도 정책 통합 테스트.
 *
 * <p>배치 리스너 전환 후 Redis 가드를 제거했으므로 Redis 관련 검증은 포함하지 않는다.
 * 검증 시나리오:
 * <ul>
 *     <li>역직렬화 실패한 메시지가 {@code notification-send-result-dlq} 토픽으로 이동</li>
 *     <li>DB 일시 장애 시 지수 백오프 재시도 후 정상 적재</li>
 *     <li>재시도 한도 초과 시 DLQ로 이동</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest(classes = ReportingTestApplication.class)
@ActiveProfiles({"worker"})
@EmbeddedKafka(partitions = 1, topics = {
        NotificationSendResultConsumer.TOPIC,
        NotificationSendResultConsumer.TOPIC + "-dlq"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationSendResultDlqIntegrationTest.FailInjectionConfig.class)
@Testcontainers
class NotificationSendResultDlqIntegrationTest {

    static final String DLQ_TOPIC = NotificationSendResultConsumer.TOPIC + "-dlq";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("butterfly_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.key-deserializer",
                () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer",
                () -> "org.springframework.kafka.support.serializer.JsonDeserializer");
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages",
                () -> "com.b101.butterfly.reporting.notification.messaging");
        registry.add("spring.kafka.consumer.properties.spring.json.value.default.type",
                () -> "com.b101.butterfly.reporting.notification.messaging.NotificationSendResultPayload");
        registry.add("spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer",
                () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        // Redis 자동설정 제외 — 배치 리스너 전환 후 컨슈머에서 Redis 가드를 제거했으므로 불필요.
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                      "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration");
    }

    @Autowired
    private ReportingNotificationLogRepository notificationLogRepository;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private FailInjectionAspect failInjection;

    @AfterEach
    void cleanUp() {
        txTemplate.executeWithoutResult(s -> notificationLogRepository.deleteAll());
        failInjection.reset();
    }

    @Test
    @DisplayName("역직렬화 실패 — 잘못된 JSON이 즉시 DLQ로 이동한다")
    void undeserializableMessageGoesStraightToDlq() {
        try (Producer<byte[], byte[]> rawProducer = rawByteProducer()) {
            byte[] garbage = "{ this is not valid json }".getBytes();
            rawProducer.send(new ProducerRecord<>(
                    NotificationSendResultConsumer.TOPIC, "bad-key".getBytes(), garbage));
            rawProducer.flush();
        }

        try (Consumer<byte[], byte[]> dlqConsumer = dlqByteConsumer()) {
            ConsumerRecord<byte[], byte[]> dlqRecord = pollOne(dlqConsumer, Duration.ofSeconds(15));
            assertThat(dlqRecord).as("DLQ에 메시지가 도달해야 한다").isNotNull();
            assertThat(dlqRecord.topic()).isEqualTo(DLQ_TOPIC);
            assertThat(new String(dlqRecord.value())).contains("this is not valid json");
        }

        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("DB 일시 장애 — 1회 실패 후 재시도하여 적재 성공")
    void transientDbErrorIsRetriedAndSucceeds() {
        failInjection.failNextNTimes(1);

        String key = "msg-retry-success";
        sendJson(NotificationSendResultConsumer.TOPIC, "k", payload(key));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findByMessageKey(key)).isPresent());

        assertThat(failInjection.invocationCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("재시도 한도 초과 — 영속 장애 시 DLQ로 이동")
    void exhaustedRetriesGoToDlq() {
        failInjection.failForever();

        String key = "msg-retry-exhausted";
        sendJson(NotificationSendResultConsumer.TOPIC, "k", payload(key));

        try (Consumer<byte[], byte[]> dlqConsumer = dlqByteConsumer()) {
            ConsumerRecord<byte[], byte[]> dlqRecord = pollOne(dlqConsumer, Duration.ofSeconds(60));
            assertThat(dlqRecord).as("재시도 한도 초과 후 DLQ에 도달해야 한다").isNotNull();
            assertThat(dlqRecord.topic()).isEqualTo(DLQ_TOPIC);
        }

        assertThat(notificationLogRepository.findByMessageKey(key)).isEmpty();
    }

    private NotificationSendResultPayload payload(String messageKey) {
        return new NotificationSendResultPayload(
                messageKey,
                1L, 10L, 100L, 5L,
                200L, 300L,
                Channel.PUSH,
                NotificationStatus.SENT,
                LocalDateTime.of(2026, 5, 6, 10, 0),
                null,
                null, null
        );
    }

    private Producer<byte[], byte[]> rawByteProducer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new org.apache.kafka.clients.producer.KafkaProducer<>(props);
    }

    private void sendJson(String topic, String key, NotificationSendResultPayload payload) {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        try (Producer<String, NotificationSendResultPayload> p =
                     new org.apache.kafka.clients.producer.KafkaProducer<>(props)) {
            p.send(new ProducerRecord<>(topic, key, payload));
            p.flush();
        }
    }

    private Consumer<byte[], byte[]> dlqByteConsumer() {
        Properties props = new Properties();
        props.putAll(KafkaTestUtils.consumerProps("dlq-test-" + System.nanoTime(), "true",
                embeddedKafkaBroker));
        props.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put("auto.offset.reset", "earliest");
        Consumer<byte[], byte[]> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        consumer.subscribe(List.of(DLQ_TOPIC));
        return consumer;
    }

    private static ConsumerRecord<byte[], byte[]> pollOne(Consumer<byte[], byte[]> consumer,
                                                          Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        return null;
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class FailInjectionConfig {
        @org.springframework.context.annotation.Bean
        FailInjectionAspect failInjectionAspect() {
            return new FailInjectionAspect();
        }
    }

    @Aspect
    static class FailInjectionAspect {

        private final AtomicInteger remainingFailures = new AtomicInteger(0);
        private final AtomicInteger invocations = new AtomicInteger(0);
        private volatile boolean failForever = false;

        void reset() {
            remainingFailures.set(0);
            invocations.set(0);
            failForever = false;
        }

        void failNextNTimes(int n) {
            failForever = false;
            remainingFailures.set(n);
        }

        void failForever() {
            failForever = true;
        }

        int invocationCount() {
            return invocations.get();
        }

        @Around("execution(* com.b101.butterfly.reporting.notification.messaging.NotificationSendResultConsumer.consume(..))")
        public Object aroundConsume(ProceedingJoinPoint pjp) throws Throwable {
            invocations.incrementAndGet();
            if (failForever) {
                throw new DataAccessResourceFailureException("simulated DB outage");
            }
            if (remainingFailures.getAndDecrement() > 0) {
                throw new DataAccessResourceFailureException("simulated transient DB error");
            }
            return pjp.proceed();
        }
    }
}
