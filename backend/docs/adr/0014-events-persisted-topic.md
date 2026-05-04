---
status: accepted
date: 2026-05-04
decision-makers: 역할 2 (event/customer 모듈)
consulted: 역할 3 (segment) — 본 토픽 구독자, 요청 originator
informed: 역할 5 (analytics/reporting) — 미래 구독 가능성
---

# ADR-0014: events.persisted 토픽 신설 — 세그먼트 실시간 매칭 happens-before 보장

## Context and Problem Statement

ADR-0011에서 정의한 events.collected 토픽은 "API가 SDK 요청을 수락한 시점"을 의미.
worker(EventConsumer)가 DB에 INSERT + event_daily_agg UPSERT를 트랜잭션으로 처리하지만,
**다른 컨슈머 그룹이 events.collected를 동시 구독하면 commit 전에 메시지를 받을 수 있음.**

segment 모듈(역할 3)이 Phase 6-3에서 실시간 세그먼트 매칭 컨슈머(SegmentEventMatchConsumer)를
구현하면서 본 race condition 발견:

```
세그먼트 정의: category=결제, period=Cumulative, minCount=1 (첫 결제 시 매칭)

t=0: user 42 첫 결제 → events.collected publish
t=1: SegmentEventMatchConsumer가 먼저 소비 → EventAggReader.userMatches(결제, 42) 호출
t=2: 그 시점 event_daily_agg에 본 이벤트 미반영 (EventConsumer commit 전)
     → SUM(event_count) = 0 < minCount(1) → 미매칭
t=3: EventConsumer 뒤늦게 commit → agg 반영. 평가는 이미 끝남
```

→ **minCount=1이 가장 흔한 케이스("첫 구매자", "회원가입 완료한 사람")인데 거의 100% 누락.**
v1 운영 신뢰도 깨짐.

## Decision Drivers

- segment minCount=1 정확성 (운영 신뢰도)
- API 응답 시간 영향 0 (publish는 worker 내부에서만 추가)
- segment 측 변경 최소화 — 단순히 다른 토픽 구독으로 전환
- 추후 다른 구독자(analytics, audit 등)도 commit 후 시점이 의미 있을 가능성

## Considered Options

### A. retry 우회 — segment 측에서 매칭 false면 200ms 후 1회 재시도

- 장점: segment 단독 변경. event 측 무관
- 단점: hacky. 200ms는 lag 큰 환경에선 부족. SLA 보장 불가. 어쨌든 race window 존재

### B. 본 이벤트 +1 보정 — agg 결과 + 1 ≥ minCount

- 장점: 토픽 분리 불필요
- 단점: agg에 본 이벤트 반영됐는지 모름 → double count 위험. eventUuid 기준 별도 dedup 필요해서 복잡도 폭발

### C. events.persisted 별 토픽 (채택)

- events.collected = "수집 시도 / API 수락"
- events.persisted = "DB 저장 + agg 반영 완료" — happens-before 보장
- 장점: 의미 명확, race 자체 제거, 운영 신뢰도, segment 측 단순
- 단점: event 모듈 변경 필요, 새 토픽 운영 부담 약간 증가

## Decision Outcome

### 채택 — 옵션 C

근거 raw 메모 (본인 언어로 다시 쓸 것):
- A는 SLA 보장 불가. 운영에서 minCount=1 누락 알람 늘어날 것
- B는 dedup 추가 비용이 토픽 분리보다 큼. 복잡도 정당화 못함
- C는 토픽 1개 추가 + AFTER_COMMIT 리스너 1개 추가로 끝. event 측 변경량 작음
- 미래 다른 구독자 (audit log read model, analytics 실시간 집계 등)도 "DB 확정 후" 의미가 더 정확 → 본 토픽 재사용 가능

### 토픽 명명

- 토픽: `events.persisted`
- ADR-0011 §3 패턴 (`<리소스>.<과거형 상태>`) 일관 — `collected` → `persisted`로 의미 차이 명확
- 같은 partition key (`projectId + ":" + userId`) — 같은 user 이벤트는 같은 partition → 동일 segment consumer 인스턴스에서 순차 처리

### Payload 형식 — 신규 record `EventPersistedPayload` (5 필드)

```java
public record EventPersistedPayload(
    String eventUuid,
    Long projectId,
    Long userId,
    Long categoryId,
    LocalDateTime occurredAt
) {}
```

기존 `EventPayload` 재사용 안 한 이유:
- segment가 안 쓰는 필드(deviceId, eventData, schemaVersion) 노출 회피
- 토픽 의미("수집 → DB 확정")에 맞춰 도메인 의도 정확히 표현
- 미래 구독자가 추가 필드 필요해지면 그때 별도 schema 진화

### Publish 시점 — `@TransactionalEventListener(phase = AFTER_COMMIT)`

```java
@KafkaListener(topics = EventProducer.TOPIC, ...)
@Transactional
public void consume(EventPayload payload) {
    int inserted = eventRepository.insertIfNotExists(...);
    if (inserted == 0) return;  // 중복 → publish 안 함 (자연 멱등)
    eventDailyAggRepository.upsertIncrement(...);

    // EventPersistedPayload을 그대로 application event로 발행 (Spring은 타입으로 라우팅)
    applicationEventPublisher.publishEvent(new EventPersistedPayload(
            payload.eventUuid(),
            payload.projectId(),
            payload.userId(),
            payload.categoryId(),
            payload.occurredAt()
    ));
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onPersisted(EventPersistedPayload payload) {
    try {
        eventPersistedProducer.send(payload);
    } catch (RuntimeException e) {
        log.error("events.persisted publish 실패. eventUuid={}", payload.eventUuid(), e);
        // tx 이미 commit됨. 누락은 segment rebuild로 복구
    }
}
```

선택 이유:
- Spring 표준 패턴, 트랜잭션 매니저가 commit 성공 후에만 fire 보장
- ApplicationEventPublisher 기반이라 단위 테스트 용이 (mock 가능)
- 대안인 `TransactionSynchronizationManager.registerSynchronization` 직접 사용은 raw API라 가독성 낮고 테스트 어려움
- **wrapper 클래스 없음** — `EventPersistedPayload`을 application event로 그대로 사용. Spring은 타입으로 listener를 라우팅하므로 별도 wrapper 불필요 (YAGNI)

### 멱등 publish

EventConsumer가 이미 `insertIfNotExists()`로 멱등 처리.
- 중복 메시지 (consumer rebalance 등) → INSERT 0건 → 도메인 이벤트 발행 안 함 → publish 안 함
- 결과: events.persisted에도 자연 멱등

추가로 segment 측이 `event:dedup:{eventUuid} 10m TTL` Redis 캐시로 자체 dedup 예정 — double safety.

### 인프라 분리 — `WorkerKafkaProducerConfig` 신규

기존 `EventKafkaProducerConfig`는 `@Profile("api")`로 events.collected publish용.
worker 프로세스에서도 publish가 필요해졌으므로 **분리된 설정 클래스 신설**:

```java
@AutoConfiguration
@Profile("worker")
public class WorkerKafkaProducerConfig {
    // ProducerFactory<String, EventPersistedPayload>
    // KafkaTemplate<String, EventPersistedPayload>
}
```

`@Profile`을 `{"api", "worker"}`로 합치는 옵션도 가능했으나, **api/worker 프로세스의 책임 분리가
명확해지고 future producer 추가 시 영향 범위 격리**되어 분리 채택.

## Consequences

### 좋은 점

- segment의 minCount=1 매칭 정확성 보장 (race 자체 제거)
- segment 측 변경 최소: 토픽 구독 대상만 events.persisted로 교체
- 다른 미래 구독자 (analytics 실시간 집계, audit) 재사용 가능
- API 응답 시간 영향 0 (publish는 worker 내부)

### 나쁜 점 / 주의

- **publish 실패 시 segment 영영 미수신** — tx 이미 commit이라 rollback 불가
  - 복구: segment rebuild 잡으로 보강 (eventual consistency)
  - 운영 모니터링: events.persisted publish 실패율 알람 필요
- 토픽 운영 부담 약간 증가 (Kafka admin, 관제 대시보드)
- worker 프로세스에 ProducerFactory 빈 추가 — 로컬 개발 환경에서 Kafka broker 의존성 강화

### 미해결

- production 배포 시 **events.persisted 토픽을 명시적으로 생성**할지 (KafkaAdmin + NewTopic 빈)
  vs auto-create 의존할지 — 별 작업으로 결정 (배포 환경 영역)
- AFTER_COMMIT 리스너의 publish 실패 모니터링 알람 (Micrometer / 운영팀 알람) — 별 작업

## Implementation Checklist

코드 (event 모듈, worker 프로세스):
- [ ] `WorkerKafkaProducerConfig` 신설 (`@Profile("worker")`)
- [ ] `EventPersistedPayload` record 신설 (5 필드, application event + Kafka payload 겸용)
- [ ] `EventPersistedProducer` 신설 (`@Profile("worker")`)
- [ ] `EventConsumer` 수정 — `ApplicationEventPublisher` 주입, `consume()` 끝에 도메인 이벤트 발행
- [ ] `EventConsumer.onPersisted()` 신설 — `@TransactionalEventListener(AFTER_COMMIT)` 메서드. publish 실패 swallow + log

테스트:
- [ ] `EventConsumerTest` 갱신 — `inserted == 0`(중복)일 때 도메인 이벤트 발행 안 됨, `inserted == 1`일 때 발행됨
- [ ] AFTER_COMMIT 동작 통합 테스트 — DB commit 후 events.persisted 토픽으로 메시지 도달 확인 (EmbeddedKafka)

소통:
- [ ] 역할 3 (segment 남정연) 통보 — 토픽명 `events.persisted`, payload 형식 (EventPersistedPayload 5필드), 머지 일정
- [ ] segment 측에서 trusted package 추가 필요: `com.b101.butterfly.event.messaging`

## More Information

- 관련 ADR: [ADR-0011 이벤트 수집 흐름](0011-event-collection-flow.md)
- 요청 originator: segment 모듈 (남정연), 2026-05-04, Phase 6-3 [-179]
- 관련 코드: `butterfly-event/messaging/EventConsumer.java`, segment의 `SegmentEventMatchConsumer` (신설 예정)
