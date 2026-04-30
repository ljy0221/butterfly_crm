---
status: accepted
date: 2026-04-28
decision-makers: 역할 2 (고객/이벤트 수집)
consulted: 역할 3 (세그먼트), 역할 5 (분석) — 향후 토픽 컨슘 가능성
---

# ADR-0011: 이벤트 수집 흐름 및 event 모듈 producer/consumer 통합

## Context and Problem Statement

`/sdk/v1/events`(API 20) 와 `/sdk/v1/events/batch`(API 21)는 클라이언트 SDK가 호출하는 트래픽 1순위 엔드포인트다.
이벤트는 캠페인 트리거, 세그먼트 산정, 분석 집계의 입력이 되므로 **유실 최소화 + 빠른 응답**이 동시에 요구된다.

다음 두 가지 결정이 함께 필요했다:

1. **수집 흐름**: 동기 DB write로 갈지, 메시지 큐를 끼고 비동기로 갈지
2. **모듈 책임 배치**: producer / consumer / entity 코드를 어느 모듈이 소유할지

이 둘은 강하게 얽혀 있어서 한 ADR로 묶어 박는다. 모듈 책임이 흐름 결정에 따라 달라지기 때문.

## Decision Drivers

- 트래픽 폭주 시(예: 캠페인 발송 직후 push 클릭 이벤트가 수만 건 동시 유입) DB connection pool이 마르면 management API까지 동반 사망 → **격리 필수**
- SDK는 모바일 클라이언트라 응답 시간이 사용자 체감에 직결 → **수백 ms 이내 응답**
- 일부 이벤트 유실은 허용되지 않음 (campaign 성과 측정 신뢰성) → **at-least-once 보장**
- MVP 일정 + 신입 학습 부담 → **운영/디버깅이 단순한 구조 우선**
- 미래 MSA 분리 가능성을 죽이지 않을 것 (하지만 지금 미리 쪼개진 않음 — ADR-0011 outside scope)

## Considered Options

### 1. 수집 흐름

| # | 흐름 | 장점 | 단점 |
|---|------|------|------|
| **A** | **API → Kafka publish → worker consume → DB write** | DB 격리, 응답 빠름, 폭주 buffer, worker 독립 스케일 | dual write(API↔Kafka) 정합성 이슈, 인프라 1개 추가 |
| B | API → DB direct write (동기) | 단순, 인프라 없음 | 폭주 시 management API 동반 다운, 응답 시간 DB write에 묶임 |
| C | API → outbox 테이블 → CDC → Kafka → worker → DB | dual write 문제 해결 | Debezium/CDC 운영 부담 큼, MVP 과설계 |

### 2. 모듈 책임 배치

| # | 위치 | 평가 |
|---|------|------|
| α | customer = producer만, worker 모듈에 consumer + entity | worker에 도메인 로직 침투 → 응집성 깨짐 |
| **β** | **customer = producer + consumer + entity 전부 소유, `@Profile`로 프로세스별 활성화** | 도메인 응집성, MSA 분리 시 패키지째 잘라내기 쉬움 |
| γ | ingest 전용 신규 모듈 신설 | over-engineering, 모듈 수만 늘어남 (YAGNI) |

## Decision Outcome

### 1. 수집 흐름 — 옵션 A (Kafka 비동기)

```
[SDK]
  │ POST /sdk/v1/events
  ▼
[butterfly-api 프로세스]
  └─ EventCollectController
        └─ EventProducer.send("events.collected", payload)
        └─ return ApiResponse.ok({ accepted: true })   ← 즉시 응답

           │ Kafka topic: events.collected
           ▼
[butterfly-worker 프로세스]
  └─ EventConsumer (@KafkaListener)
        └─ EventRepository.save(event)   ← 멱등 처리
```

- 응답은 `{ "accepted": true }` 즉시 반환 (publish 성공 = 수락)
- publish 실패 시 5xx 반환 → SDK가 동일 `eventId`로 재전송
- consumer는 `eventId` UNIQUE KEY로 중복 insert 무시 → at-least-once 보장

### 2. 모듈 책임 — 옵션 β (customer 모듈 단일 소유)

```
butterfly-customer/
├── controller/EventCollectController          ← api 프로세스에서 활성화
├── messaging/
│   ├── EventProducer                          ← api 프로세스에서 활성화
│   ├── EventConsumer (@KafkaListener)         ← worker 프로세스에서 활성화
│   └── EventPayload                           (publish/consume 양쪽 공유 DTO)
├── entity/Event, EventCategory                ← 양쪽 다 (스키마 owner)
├── repository/EventRepository
└── config/
    ├── ApiCustomerConfig    @Profile("api")
    └── WorkerCustomerConfig @Profile("worker")
```

- 같은 코드베이스, 다른 프로파일로 다른 Bean 활성화
- `butterfly-api`와 `butterfly-worker`는 각각 `spring.profiles.active=api` / `=worker`로 기동
- customer 모듈 자체는 어떤 프로세스로 떠 있는지 모름 — `@Profile` 분기에 위임

**패키지명 `messaging/` 선택 이유**:
- 도메인 코드가 인프라 기술명(`kafka`)을 직접 노출하지 않음 (Hexagonal/Clean Architecture 원칙)
- 다른 도메인 모듈(delivery, campaign 등)이 메시징 도입 시 같은 패키지명 컨벤션 공유 → 모듈 간 일관성
- `controller / service / repository / entity / messaging` 모두 **역할** 기반 분류로 일관 (기술명 섞이지 않음)
- 추후 한 모듈에서 Kafka + RabbitMQ 둘 다 쓰게 되면 `messaging/kafka/`, `messaging/rabbit/`로 자연스럽게 세분화

**producer/consumer 폴더 분리 안 함**:
- 둘이 `EventPayload` schema를 공유 → 같이 바뀌는 코드는 가까이 둠 (Common Closure Principle)
- `@Profile` 어노테이션이 클래스 레벨에 명시되므로 어느 프로세스용인지 파일 열면 바로 보임
- 파일 개수가 5개 미만이라 폴더 분리는 grep 동선만 길어짐 (premature structuring)

### 3. 토픽 명명 — `events.collected`

- 패턴: `<리소스>.<과거형 상태>` (모듈명이 곧 리소스명일 때 도메인 prefix 생략)
- 미래에 다른 모듈이 자기 events를 도입(예: `audit.events.*`)하면 그때 prefix 형태(`tracking.events.collected` 등)로 후속 ADR로 재명명
- 멀티테넌트 분리는 토픽이 아닌 payload의 `projectId`로 처리 (per-tenant 토픽은 폭발)
- 버전은 토픽명에 박지 않고 schema registry 또는 payload 필드로 처리 (현 단계 over-engineering 회피)

### 4. 멱등 키 — `event_uuid` 별도 컬럼

events 테이블에 멱등 키 전용 컬럼 추가:

```sql
event_uuid VARCHAR(36) NOT NULL UNIQUE COMMENT '멱등 키 (API 서버 부여 UUID)'
```

- **PK는 기존 `event_id BIGINT AUTO_INCREMENT` 유지** (다른 테이블 FK 영향 0, InnoDB clustered index 효율 유지)
- **API 서버(producer)가 publish 직전** `UUID.randomUUID()` 생성, payload에 포함
- consumer는 `INSERT ... ON DUPLICATE KEY UPDATE event_id = event_id`로 중복 시 no-op
- `event_daily_agg` 증분은 events insert 결과(신규 row)일 때만 → agg도 자연스럽게 멱등

**왜 UUID를 PK가 아닌 별도 컬럼으로?**
InnoDB는 PK가 clustered index. UUID v4는 랜덤이라 PK로 쓰면 page split, fragmentation, buffer pool 캐시 미스 등으로 high-volume insert 성능 2~3배 저하. PK는 BIGINT auto-increment (시간 단조 증가)로 유지하고 UUID는 secondary unique 컬럼으로.

**막아주는 시나리오**: Consumer offset commit 실패, rebalancing, 운영 재처리, producer 재전송.
**못 막는 시나리오**: 클라이언트 SDK 네트워크 timeout 재시도 (server가 매번 새 UUID 부여) — 별도 ADR(Idempotency-Key 패턴)로 추후 처리.

상세 근거 및 다른 옵션(Snowflake, 자연 복합키, 멱등 키 없음) 비교는 [개인 ADR-0001](../../../docs/decisions/0001-이벤트멱등키-uuid별도컬럼.md) 참조.

### 5. Kafka partition key — `(projectId, userId)`

```java
kafkaTemplate.send(
    "events.collected",
    payload.projectId() + ":" + payload.userId(),  // partition key
    payload
);
```

**왜 `(projectId, userId)`?**
- 같은 유저 이벤트는 항상 **같은 partition** → 같은 consumer 인스턴스가 **순차 처리** → `event_daily_agg` 같은 row에 동시 update가 발생하지 않음 → row-level lock contention 회피
- 다른 유저는 다른 partition으로 분산 → consumer 처리량 유지
- `projectId` 포함 이유: 여러 프로젝트의 동일 user_id가 우연히 같은 partition으로 몰리는 걸 방지 (멀티테넌트 격리)

면접 narrative: "Kafka partition key를 `(projectId, userId)`로 설정해 같은 유저 이벤트는 단일 consumer에서 순차 처리되도록 했고, 결과적으로 hot row lock contention을 partition 레벨에서 회피했습니다."

### 구현 체크리스트 (역할 2 주도)

- [ ] `butterfly-customer/build.gradle`에 `spring-kafka` 추가 ✅ (본 ADR과 함께 처리)
- [ ] `butterfly-customer/docs/MODULE.md` 갱신 ✅ (본 ADR과 함께 처리)
- [ ] `02-customer.md` API 20/21에 토픽명 명시 ✅ (본 ADR과 함께 처리)
- [ ] 패키지 골격: `controller / messaging / entity / repository / config` 생성
- [ ] `EventProducer`, `EventConsumer` 스켈레톤 + `@Profile` 분리
- [ ] `events` 테이블 `eventId` UNIQUE KEY DDL 반영 (ERD 갱신 필요할 수 있음)
- [ ] `butterfly-api` / `butterfly-worker`의 `application.yml`에 프로파일별 Kafka 설정 분리 (producer는 api만, consumer는 worker만)
- [ ] consumer에서 `DuplicateKeyException` 처리 + 통합 테스트

## Consequences

### 좋은 점

- **트래픽 격리**: management API와 events write가 다른 프로세스 + 다른 DB connection pool. 폭주 시 한쪽만 영향
- **응답 시간 안정**: SDK 응답이 DB write에 묶이지 않음. publish 성공만으로 즉시 반환
- **독립 스케일**: worker는 KEDA로 Kafka consumer lag 기준 자동 스케일링 가능 (K8s 토폴로지 활용)
- **도메인 응집성**: 이벤트의 producer/consumer/entity가 한 모듈에 있어 schema 변경 시 한 곳만 수정
- **MSA 진화 경로 보존**: customer 패키지를 통째로 잘라 별도 서비스로 분리 가능

### 나쁜 점 / 주의

- **dual write 정합성**: producer가 publish 성공 후 consumer가 DB write 실패하면 "수락됐지만 저장 안 된 이벤트"가 잠시 존재. consumer는 재시도로 복구하지만 Kafka 클러스터 자체 장애 시엔 outbox 패턴이 필요해짐 → MVP 외 범위
- **인프라 의존 추가**: Kafka 클러스터 운영 부담 (StatefulSet on K8s, 디스크 관리, partition 설계). 학습 가치는 있으나 장애 1포인트 추가
- **개발/테스트 복잡도**: 로컬에서 이벤트 흐름 검증하려면 Kafka 띄워야 함. testcontainers 사용 권장
- **`@Profile` 누락 리스크**: api 프로세스가 실수로 consumer Bean을 띄우거나, worker가 controller를 띄우면 의도치 않은 동작. 통합 테스트 + 빈 등록 검증 로직 필요
- **`eventId` 부재 시 멱등성 깨짐**: SDK가 `eventId`를 안 넣으면 재시도 시 중복 적재. SDK 계약에 `eventId` 필수 명시 필요 (API 20/21 request schema에 반영 예정)

## More Information

- 관련 ADR: [ADR-0002 캠페인 정의/실행 분리](0002-campaign-definition-execution-split.md) (api/worker 분리 패턴의 선례)
- 관련 노트: [2026-04-27 백엔드 아키텍처 딥다이브](../notes/2026-04-27-architecture-deep-dive.md) §3, §4, §8
- 관련 API 문서: [02-customer.md API 20/21](../api/02-customer.md)
- 관련 모듈 문서: [butterfly-customer/docs/MODULE.md](../../butterfly-customer/docs/MODULE.md)
- requirements: 이벤트 수집 throughput, SDK 응답 시간 SLO
