package com.b101.butterfly.reporting.notification.messaging;

import com.b101.butterfly.reporting.AbstractReportingMySqlTest;
import com.b101.butterfly.reporting.ReportingTestApplication;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.NotificationLogBulkRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Kafka 배치 리스너 → {@link NotificationSendResultConsumer} → {@code notification_logs} 끝단 통합 테스트.
 *
 * <p>4ff4006c 에서 삭제된 EmbeddedKafka 기반 테스트를 대체한다. 삭제 사유가 EmbeddedKafka +
 * ErrorHandlingDeserializer + AckMode.BATCH 조합의 DLQ producer 초기화 실패였으므로
 * 실제 브로커(Testcontainers Kafka)로 바꿔 그 조합을 피한다.
 *
 * <p>이 테스트가 고정하는 것 세 가지:
 * <ol>
 *   <li>배치 INSERT SQL 이 실제 스키마와 맞는다 (스키마에 없는 컬럼 사용 시 적재 0건으로 실패한다).</li>
 *   <li>{@code @Transactional} 이 걸린 배치 리스너가 {@code List<>} 파라미터로 정상 호출된다
 *       (4ff4006c 가 "프록시가 매칭을 방해할 수 있다"고 가정하고 제거한 근거를 실측으로 확인).</li>
 *   <li>배치 도중 예외가 나면 그 배치의 INSERT 가 전부 롤백된다 ({@code @Transactional} 제거 시 실패).</li>
 * </ol>
 * 범위 밖: DLQ 라우팅과 재시도 백오프(별도 테스트), Flyway 마이그레이션과의 일치(이 모듈 테스트는 ddl-auto 로 스키마를 만든다).
 */
@SpringBootTest(classes = ReportingTestApplication.class, properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@ActiveProfiles("worker")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationSendResultConsumerIntegrationTest extends AbstractReportingMySqlTest {

    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private NotificationSendResultConsumer consumer;

    /** 실제 빈을 감싸 두고, 필요한 테스트에서만 "INSERT 직후 예외"를 주입한다. */
    @MockitoSpyBean private NotificationLogBulkRepository bulkRepository;

    @AfterEach
    void cleanUp() {
        Mockito.reset(bulkRepository);
        jdbcTemplate.execute("DELETE FROM notification_logs");
    }

    @Test
    @DisplayName("정상 적재 — 발행한 12건이 모두 notification_logs 에 들어간다")
    void batchOfMessagesIsPersisted() {
        List<NotificationSendResultPayload> payloads = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            payloads.add(payload("normal-" + i));
        }

        publish(payloads);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(countByKeyPrefix("normal-")).isEqualTo(12));
    }

    @Test
    @DisplayName("재전달 — 같은 messageKey 를 두 번 발행해도 1행만 남는다 (INSERT IGNORE 멱등성)")
    void redeliveredMessageKeyIsStoredOnce() {
        publish(List.of(payload("dup-1"), payload("dup-1")));
        publish(List.of(payload("dup-1"), payload("dup-marker")));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(countByKeyPrefix("dup-marker")).isEqualTo(1));
        assertThat(countByKeyPrefix("dup-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("blank messageKey — 건너뛰고 나머지는 적재한다")
    void blankMessageKeyIsSkipped() {
        publish(List.of(payload(""), payload("  "), payload("blank-ok")));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(countByKeyPrefix("blank-ok")).isEqualTo(1));
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("배치 도중 예외 — INSERT 직후 예외가 나면 그 배치가 전부 롤백된다")
    void exceptionAfterInsertRollsBackWholeBatch() {
        AtomicBoolean fail = new AtomicBoolean(true);
        Mockito.doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (fail.get()) {
                throw new IllegalStateException("INSERT 직후 장애 주입");
            }
            return result;
        }).when(bulkRepository).bulkInsert(Mockito.anyList());

        List<NotificationSendResultPayload> batch = List.of(
                payload("rb-1"), payload("rb-2"), payload("rb-3"));

        // 트랜잭션 프록시가 JPA 예외 변환을 적용하므로 타입이 아니라 주입한 메시지로 확인한다.
        assertThatThrownBy(() -> consumer.consume(batch)).hasMessageContaining("장애 주입");

        // @Transactional 이 없으면 batchUpdate 가 auto-commit 으로 이미 확정돼 3행이 남는다.
        assertThat(countByKeyPrefix("rb-")).isZero();

        // 장애가 사라진 뒤 같은 배치를 다시 받으면(재전달) 정상 적재된다.
        fail.set(false);
        consumer.consume(batch);
        assertThat(countByKeyPrefix("rb-")).isEqualTo(3);
    }

    private long count() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_logs", Long.class);
        return n == null ? 0 : n;
    }

    private long countByKeyPrefix(String prefix) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_logs WHERE message_key LIKE ?", Long.class, prefix + "%");
        return n == null ? 0 : n;
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
                null, null);
    }

    private void publish(List<NotificationSendResultPayload> payloads) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        KafkaTemplate<String, NotificationSendResultPayload> template =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        try {
            payloads.forEach(p -> template.send(NotificationSendResultConsumer.TOPIC, p.messageKey(), p));
            template.flush();
        } finally {
            template.destroy();
        }
    }
}
