package com.b101.butterfly.event.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.b101.butterfly.event.EventTestApplication;
import com.b101.butterfly.event.repository.EventRepository;
import com.b101.butterfly.event.support.AbstractMySqlContainerTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
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

/**
 * Producer → EmbeddedKafka → Consumer → MySQL → AFTER_COMMIT → events.persisted 까지 전체 흐름 E2E.
 *
 * <p>운영에선 api/worker 두 프로세스로 분리되지만 ({@code @Profile} 기반), 본 테스트에선 wiring 검증을 위해
 * 양 프로파일을 동시 활성화해 한 컨텍스트에서 producer + consumer를 띄운다.
 *
 * <p>잡으려는 회귀: Kafka JSON 직렬화/역직렬화 깨짐, {@code @KafkaListener} containerFactory 빈 이름 오타,
 * {@code @Profile} 라벨 오타, partition key 포맷 변경, AFTER_COMMIT 리스너 미발화 (ADR-0014).
 */
@Tag("integration")
@SpringBootTest(classes = EventTestApplication.class)
@ActiveProfiles({"api", "worker"})
@EmbeddedKafka(partitions = 1, topics = {EventProducer.TOPIC, EventPersistedProducer.TOPIC})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventCollectFlowEndToEndTest extends AbstractMySqlContainerTest {

  @DynamicPropertySource
  static void registerKafka(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers",
        () -> System.getProperty("spring.embedded.kafka.brokers"));
    registry.add("spring.kafka.producer.key-serializer",
        () -> "org.apache.kafka.common.serialization.StringSerializer");
    registry.add("spring.kafka.producer.value-serializer",
        () -> "org.springframework.kafka.support.serializer.JsonSerializer");
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    registry.add("spring.kafka.consumer.key-deserializer",
        () -> "org.apache.kafka.common.serialization.StringDeserializer");
    registry.add("spring.kafka.consumer.value-deserializer",
        () -> "org.springframework.kafka.support.serializer.JsonDeserializer");
    registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages",
        () -> "com.b101.butterfly.event.messaging");
    registry.add("spring.kafka.consumer.properties.spring.json.value.default.type",
        () -> "com.b101.butterfly.event.messaging.EventPayload");
  }

  @Autowired
  private EventProducer eventProducer;
  @Autowired
  private EventRepository eventRepository;
  @Autowired
  private TransactionTemplate txTemplate;
  @Autowired
  private EmbeddedKafkaBroker embeddedKafka;

  @AfterEach
  void cleanUp() {
    txTemplate.executeWithoutResult(status -> eventRepository.deleteAll());
  }

  private EventPayload payload(String uuid) {
    return new EventPayload(
        uuid, 100L, 7L, 42L, 1L,
        LocalDateTime.of(2026, 4, 30, 12, 0), 1, "{\"k\":\"v\"}"
    );
  }

  @Test
  @DisplayName("골든패스 — publish 시 consumer가 events 테이블에 1행 적재")
  void publishedEventReachesDatabase() {
    eventProducer.send(payload("e2e-uuid-1"));

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
        assertThat(eventRepository.findAll())
            .singleElement()
            .satisfies(e -> {
              assertThat(e.getEventUuid()).isEqualTo("e2e-uuid-1");
              assertThat(e.getProjectId()).isEqualTo(100L);
              assertThat(e.getUserId()).isEqualTo(7L);
              assertThat(e.getCategoryId()).isEqualTo(1L);
            }));
  }

  @Test
  @DisplayName("멱등 보장 — 같은 event_uuid 두 번 publish해도 events는 1행 (consumer 측 INSERT IGNORE)")
  void duplicatePublishesResultInSingleRow() {
    EventPayload p = payload("e2e-uuid-dup");

    eventProducer.send(p);
    eventProducer.send(p);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
        assertThat(eventRepository.findAll()).hasSize(1));

    // 잠깐 더 기다려도 추가 적재 없음 검증
    await().pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
        assertThat(eventRepository.findAll()).hasSize(1));
  }

  @Test
  @DisplayName("AFTER_COMMIT — DB commit 성공 후 events.persisted 토픽으로 메시지 전달 (ADR-0014)")
  void persistedTopicReceivesMessageAfterDbCommit() {
    try (Consumer<String, EventPersistedPayload> testConsumer = persistedTopicConsumer()) {
      eventProducer.send(payload("e2e-persisted-1"));

      ConsumerRecord<String, EventPersistedPayload> record =
          KafkaTestUtils.getSingleRecord(testConsumer, EventPersistedProducer.TOPIC,
              Duration.ofSeconds(10));

      assertThat(record.key()).isEqualTo("100:7");
      EventPersistedPayload p = record.value();
      assertThat(p.eventUuid()).isEqualTo("e2e-persisted-1");
      assertThat(p.projectId()).isEqualTo(100L);
      assertThat(p.userId()).isEqualTo(7L);
      assertThat(p.categoryId()).isEqualTo(1L);
      assertThat(p.occurredAt()).isEqualTo(LocalDateTime.of(2026, 4, 30, 12, 0));

      assertThat(eventRepository.findAll()).hasSize(1);
    }
  }

  @Test
  @DisplayName("AFTER_COMMIT 멱등 — 중복 event_uuid 시 events.persisted는 1번만 publish")
  void persistedTopicNotPublishedOnDuplicate() {
    try (Consumer<String, EventPersistedPayload> testConsumer = persistedTopicConsumer()) {
      EventPayload p = payload("e2e-persisted-dup");
      eventProducer.send(p);
      eventProducer.send(p);

      await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
          assertThat(eventRepository.findAll()).hasSize(1));

      ConsumerRecords<String, EventPersistedPayload> records =
          KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(3));
      assertThat(records.count()).isEqualTo(1);
    }
  }

  private Consumer<String, EventPersistedPayload> persistedTopicConsumer() {
    Map<String, Object> props = KafkaTestUtils.consumerProps(
        "test-persisted-consumer-" + System.nanoTime(), "true", embeddedKafka);
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer",
        "org.springframework.kafka.support.serializer.JsonDeserializer");
    props.put("spring.json.trusted.packages", "com.b101.butterfly.event.messaging");
    props.put("spring.json.value.default.type",
        "com.b101.butterfly.event.messaging.EventPersistedPayload");
    Consumer<String, EventPersistedPayload> consumer =
        new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
    consumer.subscribe(java.util.List.of(EventPersistedProducer.TOPIC));
    // 동일 클래스 내 다른 테스트 메서드가 publish한 메시지가 토픽에 누적되어 있을 수 있음.
    // 본 테스트가 send 하기 전 시점까지의 메시지를 drain해서 격리.
    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1));
    return consumer;
  }
}
