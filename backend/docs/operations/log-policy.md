# Butterfly 로그 정책

> Owner: 이재영
> 최초 작성: 2026-04-28
> 관련 Jira: `S14P31B101-49`, `S14P31B101-58`
> 관련 ADR: [ADR-0008](../adr/0008-audit-log-architecture-v2.md)

## 1. 운영 로그 vs 감사 로그

| 구분 | 운영 로그 (operational log) | 감사 로그 (audit log) |
|---|---|---|
| 목적 | 애플리케이션 동작 흐름·성능·장애 원인 추적 | 보안 민감 액션의 불가변 기록 |
| 저장소 | stdout/stderr → ELK/Loki에서 수집 | `audit_logs` 테이블 (append-only) |
| 보존 | 30일 (운영), 90일 (분석용 콜드) | 최소 1년에서 최대 3년 (컴플라이언스 요구에 따라 조정) |
| 변조 방지 | 미적용 (수집 후 immutable) | 트리거 + DB GRANT + Hibernate `@Immutable`의 세 겹 방어선 ([ADR-0008](../adr/0008-audit-log-architecture-v2.md)) |
| 적재 주체 | 개발자가 `log.info/warn/error(...)` | 도메인 서비스가 `auditLogService.record(...)` 명시 호출 |
| 조회 권한 | 운영팀(전체) | OWNER (자기 프로젝트), ADMIN (전체) |

**원칙**: 보안 민감 액션은 **둘 다** 남긴다. 운영 로그에는 "발생한 일" + 추적 ID, 감사 로그에는 "누가/언제/무엇을 변경" + before/after.

## 2. 공통 로그 필드 표준

운영 로그(JSON 출력)는 다음 필드를 갖춘다. logback 구성은 [`logback-spring.xml`](../../butterfly-api/src/main/resources/logback-spring.xml) 참고.

### 필수 필드

| 필드 | 출처 | 예 |
|---|---|---|
| `timestamp` | logback 자동 | `2026-04-28T15:32:01.234+09:00` |
| `level` | logback 자동 | `INFO` |
| `logger` | logback 자동 | `c.b.b.analytics.AggregationService` |
| `message` | 호출 인자 | `"hourly aggregation done in 1240ms"` |
| `app` | logback `customFields` | `butterfly-api` / `butterfly-worker` |
| `requestId` | MDC (`RequestIdFilter` 또는 컨슈머) | UUID |
| `projectId` | MDC (인증 필터 또는 컨슈머가 세팅) | `123` |
| `memberId` | MDC (인증 필터가 세팅) | `501` |
| `traceId` | MDC (분산 추적 도입 시) | (선택) |

### 선택 필드 (HTTP 요청 로그 등)

`httpMethod, httpPath, status, durationMs, exception`

### MDC 라이프사이클

- HTTP 요청: `RequestIdFilter`가 진입에 세팅, finally에서 제거 ([RequestIdFilter.java](../../butterfly-common/src/main/java/com/b101/butterfly/global/observability/RequestIdFilter.java))
- 메시지 컨슈머: 컨슈머가 메시지 헤더에서 `X-Request-Id`를 읽어 MDC에 직접 put, 처리 끝나면 remove
- 비동기 작업(`@Async`): `MDCContext` 또는 `TaskDecorator`로 부모 스레드의 MDC를 자식 스레드에 복사

## 3. 로그 레벨 정책

| 레벨 | 사용 시점 | 예 |
|---|---|---|
| `ERROR` | 사용자 요청이 실패했고, 시스템적 원인이 있는 경우. 페이저/알림 대상 | DB 연결 끊김, 외부 API 5xx 반복, 발송 워커 OOM |
| `WARN` | 동작은 했으나 비정상이거나 곧 문제가 될 신호 | 재시도 후 성공, 리소스 한도 80% 도달, deprecated API 호출 |
| `INFO` | 정상 흐름의 의미 있는 분기점 | 요청 수신, 발송 성공, 배치 시작/종료 |
| `DEBUG` | 개발 디버깅용 상세. prod에서 비활성화 | SQL 파라미터 값, 외부 API 요청/응답 본문 |
| `TRACE` | 매우 상세한 흐름. 거의 사용하지 않음 | |

**금지 패턴**:
- 정상 흐름인데 `WARN`/`ERROR`로 남기기 (알림 노이즈)
- 사용자 입력 검증 실패를 `ERROR`로 남기기 (`WARN` 또는 정보성 INFO)

## 4. 마스킹 규칙

다음 값은 **로그 어디에도 평문으로 남기지 않는다**:

| 대상 | 처리 |
|---|---|
| 비밀번호, JWT, 세션 토큰 | 저장 금지 (어떤 형태로든) |
| API Key (Public/Secret), FCM service account JSON | 저장 금지. ID/해시만 가능 |
| 전화번호 | 가운데 4자리 마스킹 (`010-****-1234`) |
| 이메일 | 로컬파트 앞 2자만 (`ab****@example.com`) |
| 카드번호, 주민번호 | 절대 금지 |

`audit_logs.detail`에 변경 추적이 필요한 비밀값은 SHA-256 앞 8자만 기록. 마스킹 책임은 **호출자(도메인 서비스)** — 로그 작성 전에 마스킹된 DTO를 만들어서 넘긴다.

## 5. 보존 정책

| 종류 | 보존 기간 | 비고 |
|---|---|---|
| 운영 로그 (hot) | 30일 | ELK/Loki 인덱스 |
| 운영 로그 (cold) | 90일 | 압축 아카이브, 분석 용도 |
| 감사 로그 | 최소 1년에서 최대 3년 | 컴플라이언스 요구에 따라 프로젝트별 조정. 월 단위 RANGE 파티셔닝 권장 ([ADR-0008](../adr/0008-audit-log-architecture-v2.md)) |
| 발송 원장 (`notification_logs`) | 1년 | 분석 모듈 별도 정책 |

보존 기간 만료 시:
- 운영 로그: 자동 삭제 (인덱스 retention)
- 감사 로그: 외부 콜드 스토리지(S3 등)로 아카이브 후 DB에서 삭제

## 6. 추적 ID 전파 (`requestId`)

```
[Client]
  │  X-Request-Id: <UUID>      (없으면 서버가 발급)
  ▼
[butterfly-api]
  │  RequestIdFilter → MDC.put("requestId", ...)
  │  로그: req=<UUID>
  │
  │  Kafka producer.send(..., headers={"X-Request-Id": <UUID>})
  ▼
[butterfly-worker]
  │  Kafka consumer가 헤더 읽어서 MDC.put("requestId", ...)
  │  로그: req=<UUID>
```

API와 Worker의 로그를 동일한 `requestId`로 grep 가능해야 한다. 컨슈머 구현 시 다음 패턴을 따른다:

```java
@KafkaListener(...)
public void onMessage(ConsumerRecord<String, NotificationResultMessage> record) {
    String requestId = headerOrUuid(record.headers(), MdcKeys.REQUEST_ID_HEADER);
    MDC.put(MdcKeys.REQUEST_ID, requestId);
    try {
        process(record.value());
    } finally {
        MDC.clear();
    }
}
```

## 7. 관련 코드 / 문서

- [`MdcKeys`](../../butterfly-common/src/main/java/com/b101/butterfly/global/observability/MdcKeys.java) — MDC 키 상수
- [`RequestIdFilter`](../../butterfly-common/src/main/java/com/b101/butterfly/global/observability/RequestIdFilter.java) — HTTP 진입 필터
- [`butterfly-api/logback-spring.xml`](../../butterfly-api/src/main/resources/logback-spring.xml)
- [`butterfly-worker/logback-spring.xml`](../../butterfly-worker/src/main/resources/logback-spring.xml)
- [ADR-0008 감사 로그 v2](../adr/0008-audit-log-architecture-v2.md)
- [SCHEMA_DESIGN.md `audit_logs` 명세](../../butterfly-reporting/docs/SCHEMA_DESIGN.md)
