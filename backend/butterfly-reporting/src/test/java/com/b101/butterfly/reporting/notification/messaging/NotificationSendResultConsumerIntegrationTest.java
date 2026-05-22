package com.b101.butterfly.reporting.notification.messaging;

import com.b101.butterfly.reporting.ReportingTestApplication;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka 배치 리스너 → NotificationSendResultConsumer → notification_logs 끝-단 통합 테스트.
 *
 * <p>EmbeddedKafka + Testcontainers MySQL 조합으로 실제 직렬화/역직렬화,
 * INSERT IGNORE 기반 멱등성까지 검증한다.
 *
 * <p>Redis 가드는 배치 리스너 전환(bulk INSERT IGNORE)으로 제거됐으므로 관련 검증 생략.
 */
@Tag("integration")
@SpringBootTest(classes = ReportingTestApplication.class)
@ActiveProfiles({"worker"})
@EmbeddedKafka(partitions = 1, topics = {NotificationSendResultConsumer.TOPIC})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NotificationSendResultConsumerIntegrationTest {

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
    }

    @Autowired
    private ReportingNotificationLogRepository notificationLogRepository;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @AfterEach
    void cleanUp() {
        txTemplate.executeWithoutResult(s -> notificationLogRepository.deleteAll());
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

    @Test
    @DisplayName("골든패스 — 메시지 1건 발행 시 notification_logs에 1행 적재")
    void singleMessageIsPersistedToDatabase() {
        send(NotificationSendResultConsumer.TOPIC, "key-1", payload("msg-key-1"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findByMessageKey("msg-key-1"))
                        .isPresent()
                        .hasValueSatisfying(log -> {
                            assertThat(log.getProjectId()).isEqualTo(1L);
                            assertThat(log.getCampaignId()).isEqualTo(10L);
                            assertThat(log.getChannel()).isEqualTo(Channel.PUSH);
                            assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
                        })
        );
    }

    @Test
    @DisplayName("멱등 보장 — 동일 messageKey 재발행 시 notification_logs는 1행 유지 (INSERT IGNORE)")
    void duplicateMessageKeyResultsInSingleRow() {
        NotificationSendResultPayload p = payload("msg-key-dup");

        send(NotificationSendResultConsumer.TOPIC, "key-dup-1", p);
        send(NotificationSendResultConsumer.TOPIC, "key-dup-2", p);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findAll()).hasSize(1));

        await().pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findAll()).hasSize(1));
    }

    @Test
    @DisplayName("FAILED 상태 — errorCode/errorMessage 정상 적재")
    void failedMessageIsPersistedWithErrorFields() {
        NotificationSendResultPayload failed = new NotificationSendResultPayload(
                "msg-key-failed",
                1L, 10L, 100L, 5L,
                200L, 300L,
                Channel.PUSH,
                NotificationStatus.FAILED,
                null, null,
                "DELIVERY_001", "Token expired"
        );

        send(NotificationSendResultConsumer.TOPIC, "key-failed", failed);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findByMessageKey("msg-key-failed"))
                        .isPresent()
                        .hasValueSatisfying(log -> {
                            assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
                            assertThat(log.getErrorCode()).isEqualTo("DELIVERY_001");
                            assertThat(log.getErrorMessage()).isEqualTo("Token expired");
                        })
        );
    }

    @Test
    @DisplayName("blank messageKey — 해당 레코드만 건너뛰고 나머지 정상 적재")
    void blankMessageKeyIsSkipped() {
        send(NotificationSendResultConsumer.TOPIC, "key-blank", payload(""));
        send(NotificationSendResultConsumer.TOPIC, "key-valid", payload("msg-key-valid"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findByMessageKey("msg-key-valid")).isPresent());

        assertThat(notificationLogRepository.findAll()).hasSize(1);
    }

    private void send(String topic, String key, NotificationSendResultPayload payload) {
        java.util.Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        try (Producer<String, NotificationSendResultPayload> p =
                     new org.apache.kafka.clients.producer.KafkaProducer<>(props)) {
            p.send(new ProducerRecord<>(topic, key, payload));
            p.flush();
        }
    }
}
