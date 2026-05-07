package com.b101.butterfly.worker.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.b101.butterfly.event.messaging.EventPayload;
import com.b101.butterfly.event.messaging.EventProducer;
import com.b101.butterfly.event.repository.EventRepository;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.entity.SegmentMemberId;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * event 저장 완료 토픽과 segment 실시간 매칭 컨슈머의 실제 연결을 검증한다.
 *
 * <p>검증 흐름:
 * <ol>
 *   <li>api 프로파일의 {@link EventProducer}가 events.collected publish</li>
 *   <li>worker 프로파일의 event consumer가 events/event_daily_agg 저장 후 events.persisted publish</li>
 *   <li>worker 프로파일의 segment consumer가 events.persisted를 받아 segment_members UPSERT</li>
 * </ol>
 */
@SpringBootTest(
    classes = EventsPersistedSegmentIntegrationTest.TestApp.class,
    properties = {
      "spring.main.web-application-type=none",
      "spring.jpa.hibernate.ddl-auto=create",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
      "spring.jpa.open-in-view=false",
      "spring.flyway.enabled=false",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.consumer.enable-auto-commit=false",
      "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
      "spring.kafka.consumer.properties.spring.json.trusted.packages=com.b101.butterfly.event.messaging",
      "spring.kafka.consumer.properties.spring.json.value.default.type=com.b101.butterfly.event.messaging.EventPayload",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
    })
@ActiveProfiles({"api", "worker"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class EventsPersistedSegmentIntegrationTest {

  private static final int REDIS_PORT = 6379;

  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
  private static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
          .withExposedPorts(REDIS_PORT);

  static {
    MYSQL.start();
    KAFKA.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void registerContainers(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
  }

  @Autowired private EventProducer eventProducer;
  @Autowired private EventRepository eventRepository;
  @Autowired private SegmentRepository segmentRepository;
  @Autowired private SegmentMemberRepository segmentMemberRepository;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private TransactionTemplate txTemplate;

  @AfterEach
  void cleanUp() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    txTemplate.executeWithoutResult(status -> {
      segmentMemberRepository.deleteAll();
      segmentRepository.deleteAll();
      eventRepository.deleteAll();
    });
  }

  @Test
  @DisplayName("events.persisted E2E — 이벤트 저장 후 READY EVENT 세그먼트에 사용자가 편입된다")
  void persistedEventUpdatesSegmentMembers() {
    Long projectId = 9101L;
    Long userId = 7001L;
    Long categoryId = 3001L;
    LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 6, 13, 30);
    Segment segment = readyEventSegment(projectId, categoryId);

    EventPayload payload =
        new EventPayload(
            "evt-segment-e2e-" + UUID.randomUUID(),
            projectId,
            userId,
            9901L,
            categoryId,
            occurredAt,
            1,
            "{\"button\":\"buy\"}");

    eventProducer.send(payload);

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(eventRepository.findAll())
          .singleElement()
          .satisfies(event -> {
            assertThat(event.getProjectId()).isEqualTo(projectId);
            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getCategoryId()).isEqualTo(categoryId);
          });

      Optional<SegmentMember> member =
          segmentMemberRepository.findById(new SegmentMemberId(segment.getId(), userId));
      assertThat(member).isPresent();
      assertThat(member.orElseThrow().getMatchedAt())
          .isCloseTo(occurredAt, withinOneSecond());
      assertThat(member.orElseThrow().getExpiresAt()).isNull();
    });
  }

  private Segment readyEventSegment(Long projectId, Long categoryId) {
    GroupNode tree =
        new GroupNode(
            LogicOp.AND,
            List.of(new EventNode(categoryId, List.of(), new Period.Cumulative(), 1)));
    Segment segment =
        Segment.builder()
            .projectId(projectId)
            .name("events.persisted E2E")
            .description("events.persisted 통합 검증용")
            .conditionTree(tree)
            .createdBy(1L)
            .build();
    segment.markReady(0, LocalDateTime.of(2026, 5, 6, 13, 0));
    return segmentRepository.saveAndFlush(segment);
  }

  private static org.assertj.core.data.TemporalUnitOffset withinOneSecond() {
    return org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS);
  }

  @SpringBootApplication(
      scanBasePackages = {
        "com.b101.butterfly.event",
        "com.b101.butterfly.segment"
      })
  static class TestApp {
  }
}
