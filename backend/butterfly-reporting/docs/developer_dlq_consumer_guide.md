# DLQ 소비/재처리 가이드 — 다른 백엔드 도메인 개발자용

> **대상**: butterfly의 다른 도메인(notification, segment 등)에서 비슷한 패턴을 도입하거나, `notification-send-result-dlq`를 소비/재처리하려는 개발자
> **티켓**: [S14P31B101-180](https://ssafy.atlassian.net/browse/S14P31B101-180)
> **작성**: 2026-05-07

이 문서는 코드와 함께 보는 실용 가이드다. 카피-페이스트로 시작할 수 있게 스니펫 위주로 정리했다.

---

## 1. 토픽 스펙

| 항목 | 값 |
|---|---|
| 원본 토픽 | `notification-send-result` |
| DLQ 토픽 | `notification-send-result-dlq` |
| Consumer group (원본) | `butterfly-analytics-notification-logs` |
| Partition 수 | 운영 기본 (현재 3, prod 환경 변경 시 조정) |
| Key serializer | `StringSerializer` |
| Value serializer | `JsonSerializer` (정상) / `ByteArraySerializer` (역직렬화 실패 case) |

---

## 2. 메시지 페이로드

원본 토픽과 DLQ 토픽 모두 동일 페이로드를 쓴다.

```java
public record NotificationSendResultPayload(
    String messageKey,           // 멱등성 키 (필수, 발신 측이 고유 보장)
    Long projectId,
    Long campaignId,
    Long campaignExecutionId,    // null 가능 — WARN만 남기고 적재
    Long messageTemplateId,      // null 가능 — WARN만 남기고 적재
    Long userId,
    Long deviceId,
    Channel channel,             // PUSH | EMAIL | SMS | KAKAO_AT
    NotificationStatus status,   // PENDING | SENT | DELIVERED | FAILED
    LocalDateTime sentAt,
    LocalDateTime deliveredAt,
    String errorCode,            // FAILED 시 채워짐
    String errorMessage          // FAILED 시 채워짐
) {}
```

JSON 예시:

```json
{
  "messageKey": "kafka-msg-abc123",
  "projectId": 123,
  "campaignId": 4521,
  "campaignExecutionId": 88123,
  "messageTemplateId": 89,
  "userId": 78901,
  "deviceId": 23456,
  "channel": "PUSH",
  "status": "SENT",
  "errorCode": null,
  "errorMessage": null,
  "sentAt": "2026-05-06T10:00:05.123",
  "deliveredAt": null
}
```

`@JsonIgnoreProperties(ignoreUnknown = true)` — 발송 팀이 필드를 추가해도 컨슈머가 깨지지 않는다.

---

## 3. DLQ 메시지 헤더 — 디버깅 정보

DLQ로 발행될 때 Spring Kafka가 자동으로 헤더에 컨텍스트를 채운다.

| 헤더 키 | 값 | 용도 |
|---|---|---|
| `kafka_dlt-original-topic` | `notification-send-result` | 원본 토픽 |
| `kafka_dlt-original-partition` | int | 원본 partition 번호 |
| `kafka_dlt-original-offset` | long | 원본 offset |
| `kafka_dlt-original-timestamp` | long | 원본 timestamp |
| `kafka_dlt-original-consumer-group` | `butterfly-analytics-notification-logs` | 어느 그룹이 실패했는지 |
| `kafka_dlt-exception-fqcn` | 예: `org.springframework.kafka.support.serializer.DeserializationException` | 예외 클래스 FQCN |
| `kafka_dlt-exception-message` | 예: `failed to deserialize` | 예외 메시지 |
| `kafka_dlt-exception-cause-fqcn` | (있을 시) 원인 예외 클래스 | 중첩된 원인 |
| `kafka_dlt-exception-stacktrace` | 전체 스택트레이스 | 디버깅 |

이 헤더로 **재시도 가능 vs 영속 실패**를 구분한다. `DeserializationException`이면 메시지 자체 문제, 그 외는 처리 시점 인프라 문제일 확률이 높다.

---

## 4. DLQ 메시지 꺼내보기 — 운영 콘솔 패턴

### 4.1 kafka-console-consumer (간단 확인)

```bash
docker exec -it butterfly-kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic notification-send-result-dlq \
    --from-beginning \
    --property print.headers=true \
    --property print.key=true \
    --property print.value=true \
    --property key.separator=" | " \
    --max-messages 10
```

### 4.2 kafkactl / kcat (헤더만 보기)

```bash
kcat -b localhost:9092 -t notification-send-result-dlq -C -e \
    -f 'OFFSET=%o KEY=%k\nHEADERS=%h\nVALUE=%s\n---\n'
```

---

## 5. DLQ를 코드에서 소비하기 — 재처리 컨슈머 작성 패턴

DLQ를 자동으로 다시 처리하고 싶다면 별도 `@KafkaListener`를 작성한다. **새 컨슈머 그룹**을 써야 원본 컨슈머에 영향이 없다.

### 5.1 단순 재발행 패턴 (역직렬화 실패는 제외)

```java
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class NotificationDlqReprocessor {

    private static final String DLQ_TOPIC = "notification-send-result-dlq";
    private static final String ORIGINAL_TOPIC = "notification-send-result";

    private final KafkaTemplate<String, byte[]> rawKafkaTemplate;

    @KafkaListener(
        topics = DLQ_TOPIC,
        groupId = "butterfly-analytics-notification-logs-dlq-reprocessor",
        autoStartup = "false"  // 수동 트리거 권장 — 운영자가 의도적으로 시작
    )
    public void reprocess(ConsumerRecord<String, byte[]> record) {
        String exceptionFqcn = headerString(record, "kafka_dlt-exception-fqcn");

        // 역직렬화 실패는 재처리해도 같은 결과 — 스킵
        if ("org.springframework.kafka.support.serializer.DeserializationException".equals(exceptionFqcn)) {
            log.warn("Skipping non-recoverable DLQ message offset={}", record.offset());
            return;
        }

        // 그 외(인프라 일시 장애)는 원본 토픽으로 재발행
        rawKafkaTemplate.send(ORIGINAL_TOPIC, record.key(), record.value());
        log.info("Re-published DLQ message to {} key={}", ORIGINAL_TOPIC, record.key());
    }

    private static String headerString(ConsumerRecord<?, ?> record, String key) {
        var header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
```

**`autoStartup = "false"` 이유**: DLQ 자동 재처리는 보통 위험하다. 같은 메시지가 또 실패하면 무한 루프(원본↔DLQ). 운영자가 원인을 분석하고 명시적으로 시작하는 게 안전.

### 5.2 수동 트리거 엔드포인트

```java
@RestController
@RequestMapping("/internal/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DlqAdminController {

    private final KafkaListenerEndpointRegistry registry;

    @PostMapping("/notification/start")
    public ApiResponse<Void> startReprocessor() {
        var container = registry.getListenerContainer("butterfly-analytics-notification-logs-dlq-reprocessor");
        if (container != null && !container.isRunning()) {
            container.start();
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/notification/stop")
    public ApiResponse<Void> stopReprocessor() {
        var container = registry.getListenerContainer("butterfly-analytics-notification-logs-dlq-reprocessor");
        if (container != null && container.isRunning()) {
            container.stop();
        }
        return ApiResponse.ok(null);
    }
}
```

---

## 6. 다른 도메인에 같은 패턴 도입하기 — 체크리스트

`butterfly-segment`나 `butterfly-event` 등에서 같은 DLQ + 재시도 + 보조 멱등성 패턴이 필요하다면:

### 6.1 의존성

`build.gradle`에 다음이 있어야 한다 (이미 있을 가능성 높음).

```groovy
implementation 'org.springframework.kafka:spring-kafka'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'

testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:kafka'
testImplementation 'org.springframework.kafka:spring-kafka-test'
testImplementation 'org.awaitility:awaitility'
```

### 6.2 Config 빈 4종 세트

reporting의 [NotificationKafkaConsumerConfig.java](../src/main/java/com/b101/butterfly/reporting/notification/config/NotificationKafkaConsumerConfig.java)를 템플릿으로 복사하고 다음을 변경:

1. `Profile`은 worker 프로세스에서만 활성화 (`@Profile("worker")`)
2. ConsumerFactory의 `VALUE_DEFAULT_TYPE`을 본인 도메인 페이로드 클래스로
3. `TRUSTED_PACKAGES`를 본인 도메인 messaging 패키지로
4. ErrorHandler의 `ExponentialBackOff` 파라미터 — 도메인 특성 고려 (실시간성 중요하면 짧게, 정확성 중요하면 길게)
5. `dlq-suffix` property 키를 도메인별로 분리 (`butterfly.kafka.<domain>.dlq-suffix`)

### 6.3 Consumer 작성 규칙

```java
@KafkaListener(
    topics = TOPIC,
    groupId = GROUP_ID,
    containerFactory = "<도메인>SendResultListenerContainerFactory"  // Config의 Bean 이름과 일치
)
@Transactional
public void consume(YourPayload payload) {
    // 1. 입력 검증 — null/blank 키는 일찍 return
    if (payload.idempotencyKey() == null) {
        log.warn("blank key — skipped");
        return;
    }

    // 2. Redis 가드로 1차 차단
    if (!messageKeyGuard.tryAcquire(payload.idempotencyKey())) {
        log.debug("guard hit — skipped");
        return;
    }

    try {
        // 3. 도메인 로직 수행
        repository.save(toEntity(payload));
    } catch (DataIntegrityViolationException e) {
        // UNIQUE 충돌 — 정상 멱등 hit, 가드는 유지
        log.debug("Duplicate skipped: {}", payload.idempotencyKey());
    } catch (RuntimeException e) {
        // 재시도 가능 예외 — 가드 release 후 throw
        messageKeyGuard.release(payload.idempotencyKey());
        throw e;
    }
}
```

### 6.4 application.properties (worker)

전역 default가 본인 도메인 페이로드와 다를 수 있으므로 ConsumerFactory에서 명시적 override 필요. 우리 reporting의 경우:

```java
props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
        NotificationSendResultPayload.class.getName());
props.put(JsonDeserializer.TRUSTED_PACKAGES,
        "com.b101.butterfly.reporting.notification.messaging");
```

이걸 안 하면 worker의 application.properties에 있는 글로벌 `EventPayload` default가 본인 페이로드를 덮어써서 `ClassCastException`이 난다.

### 6.5 통합 테스트 패턴

[NotificationSendResultDlqIntegrationTest.java](../src/test/java/com/b101/butterfly/reporting/notification/messaging/NotificationSendResultDlqIntegrationTest.java)의 패턴을 그대로 따라간다.

핵심 포인트 — **KafkaTemplate 빈을 주입받지 말고 EmbeddedKafkaBroker로 직접 producer 만들기**. 본 모듈이 DLQ용 ProducerFactory를 추가하면 default `kafkaTemplate`이 사라진다.

```java
@Autowired
private EmbeddedKafkaBroker embeddedKafkaBroker;

private void send(String topic, String key, YourPayload payload) {
    Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    try (Producer<String, YourPayload> p = new KafkaProducer<>(props)) {
        p.send(new ProducerRecord<>(topic, key, payload));
        p.flush();
    }
}
```

DB 장애 시뮬레이션도 AOP 패턴 (FailInjectionAspect) 그대로 복사.

---

## 7. 자주 묻는 질문

### Q1. DLQ 메시지가 쌓이는데 자동으로 처리해도 되나?

**원칙적으로 NO.** 왜 DLQ로 갔는지 원인을 모르면 자동 재처리는 위험하다. 메시지 자체가 망가진 거면(역직렬화 실패) 100번 재발행해도 결과 같음. 인프라 일시 장애였으면 자동 재처리해도 되는데, 그건 헤더의 `kafka_dlt-exception-fqcn`을 보고 분기해야 함.

가이드: **운영자가 헤더 확인 → 재처리 가능 메시지만 수동 트리거**.

### Q2. 같은 messageKey가 다른 payload로 두 번 들어오면?

Redis 가드는 최초 메시지만 통과시키고 두 번째는 차단한다. 즉 **첫 메시지의 내용으로 적재되고 두 번째는 사라진다**.

이는 발신 측(이서연 팀)이 messageKey 고유성을 보장한다는 전제. 만약 발신 측이 키를 재사용하면 데이터 정확성 깨짐 — 발신 측과 합의된 계약 위반이므로 양쪽 모두에서 검증 필요.

### Q3. Redis가 죽으면 어떻게 되나?

`tryAcquire()`가 RuntimeException을 잡아 `true`를 반환 (fail-open). 그 결과 모든 메시지가 DB까지 도달하고, 중복은 DB UNIQUE 제약이 막는다. **정확성은 유지되지만 부하가 평소보다 증가**한다 (Redis로 거를 수 있던 중복까지 DB 트랜잭션 발생).

### Q4. 컨슈머 lag이 늘어난다. 재시도 백오프 때문인가?

가능성 있다. `DefaultErrorHandler`의 백오프는 같은 partition에서 blocking이라, DB 장애 시 그 partition의 다른 메시지도 멈춘다. 최대 blocking 시간은 `maxElapsedTime=20s`. 트래픽이 높고 DB 장애가 잦다면:

- partition 수 늘리기 (parallelism 증가)
- `RetryTopicConfiguration` (non-blocking) 패턴으로 전환

### Q5. 재시도 횟수/간격을 바꾸려면?

[NotificationKafkaConsumerConfig.java](../src/main/java/com/b101/butterfly/reporting/notification/config/NotificationKafkaConsumerConfig.java)의 `notificationSendResultErrorHandler` Bean의 `ExponentialBackOff` 파라미터 수정. 운영 환경별로 다르게 하려면 `@Value`로 property화.

### Q6. DLQ도 DLQ가 필요한가?

DLQ 발행 자체가 실패하면 ErrorHandler 내부에서 예외가 발생하지만, 우리는 그 케이스에 대한 별도 fallback이 없다. 보통 Kafka 브로커가 살아있는 한 DLQ 발행은 성공한다. **만약 브로커 자체가 죽었다면 컨슈머 전체가 멈추므로 DLQ를 걱정할 단계가 아니다**.

---

## 8. 관련 코드 빠른 참조

| 파일 | 역할 |
|---|---|
| [NotificationKafkaConsumerConfig.java](../src/main/java/com/b101/butterfly/reporting/notification/config/NotificationKafkaConsumerConfig.java) | ConsumerFactory + ErrorHandler + ProducerFactory + KafkaTemplate Bean |
| [NotificationSendResultConsumer.java](../src/main/java/com/b101/butterfly/reporting/notification/messaging/NotificationSendResultConsumer.java) | `@KafkaListener` + 비즈니스 로직 |
| [NotificationSendResultPayload.java](../src/main/java/com/b101/butterfly/reporting/notification/messaging/NotificationSendResultPayload.java) | 메시지 DTO (record) |
| [RedisMessageKeyGuard.java](../src/main/java/com/b101/butterfly/reporting/notification/messaging/RedisMessageKeyGuard.java) | Redis SETNX 보조 멱등성 가드 |
| [NotificationSendResultDlqIntegrationTest.java](../src/test/java/com/b101/butterfly/reporting/notification/messaging/NotificationSendResultDlqIntegrationTest.java) | DLQ + 재시도 시나리오 통합 테스트 |
| [NotificationSendResultConsumerIntegrationTest.java](../src/test/java/com/b101/butterfly/reporting/notification/messaging/NotificationSendResultConsumerIntegrationTest.java) | 골든패스 + 멱등성 + Redis 가드 통합 테스트 |
