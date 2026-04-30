# 역할 5 회의 결과: 발송 워커 관련 Blocker 3건 합의

<!-- markdownlint-disable MD022 MD032 MD029 -->

**날짜:** 2026-04-22
**참석자:** 역할 5 (발송 워커), 역할 6 (분석/통합 검증)
**상태:** ✅ 합의 완료

## 합의 결과 요약

분석 집계 테이블(ADR-0003) 구현 전 필수 3개 항목 모두 합의 완료.
- **Blocker #2** ✅ notification_logs.project_id + 인덱스 2개
- **Blocker #3** ✅ 타임스탐프 기반 카운트 + Redis 캐시 배치 UPDATE
- **Blocker #4** ✅ HMAC-SHA256 콜백 서명 토큰

다음 일정: ADR-0006 작성 후 분석 엔티티/레포/서비스 구현 착수.

---

## Blocker #2: `notification_logs.project_id` 컬럼 추가 + 인덱스

### 현황
- ERD(`backend/docs/erd.md`)에 `notification_logs`는 있지만 `project_id` 컬럼이 없음
- 현재 Worker 로직이 `project_id` 없이 로그를 남길 수 있는 경로가 있는지 확인 필요

### 문제
1. **멀티테넌시 규칙 위반** — 모든 도메인 테이블은 `project_id`를 선두 인덱스로 가져야 한다 (CLAUDE.md / requirements 4-5)
2. **대시보드 성능 저하** — 집계 테이블 재계산 배치가 `notification_logs`에서 지난 25시간을 스캔할 때, `project_id` 인덱스 없으면 전체 로그를 테이블 스캔해야 함
3. **테넌트 격리 불완전** — `project_id` 없으면 DB 레벨에서 테넌트 간 데이터 누수 방지 불가

### 제안: 두 개 인덱스 병행

```sql
ALTER TABLE notification_logs 
ADD COLUMN project_id BIGINT NOT NULL 
AFTER id;

-- 1. 재계산 배치용 (시간 기반 전체 스캔)
-- 배치 SQL: WHERE project_id = ? AND sent_at >= DATE_SUB(NOW(), INTERVAL 25 HOUR)
ALTER TABLE notification_logs 
ADD INDEX idx_nlog_project_sent (project_id, sent_at);

-- 2. 캠페인별 조회용 (프로젝트 + 캠페인 드릴다운)
-- API SQL: WHERE project_id = ? AND campaign_id = ? AND execution_id = ? 
--          AND sent_at >= ? AND sent_at < ?
ALTER TABLE notification_logs 
ADD INDEX idx_nlog_project_campaign_sent (project_id, campaign_id, sent_at);
```

**인덱스 선택 근거:**
- `idx_nlog_project_sent` → 재계산 배치 (Worker 1시간 주기)
- `idx_nlog_project_campaign_sent` → 대시보드/성과 비교 API (실시간 조회)
- 둘 다 `project_id` 선두 → 멀티테넌시 격리 강제 (DB 레벨)

### 영향도
- Worker `NotificationLogRepository.save()` 시 항상 `project_id` 설정 필수
- 현재 레포지토리 구현은 `.gitkeep`이므로 새로 작성할 때 반영
- 데이터 마이그레이션: 기존 행이 있다면 project_id 채우기 필요 (현재 MVP이므로 데이터 없음)
- 인덱스 저장소: 2개 인덱스 추가 (MySQL InnoDB 클러스터 구조), MVP 규모에서 무시할 수준

### 합의 항목
- [x] `notification_logs.project_id` 컬럼 추가 **필수** ✅ 합의 완료
- [x] 인덱스: `(project_id, sent_at)` + `(project_id, campaign_id, sent_at)` 병행 ✅ 합의 완료
- [x] Worker 저장 로직에서 `project_id` 필수 설정 ✅ ADR-0006에서 명시

### 추가 논의사항: Worker 실시간 SELECT 패턴 최소화

**배경:** notification_logs는 가장 큰 테이블이 될 가능성이 높으므로, Worker의 빈번한 SELECT는 성능 저하 초래.

#### 현재 설계 (권장)
Worker가 발송/콜백 이벤트를 수신하면 **해당 행만 UPDATE** (기존 행 존재 가정):

```java
// Worker: 발송 이벤트 처리
UPDATE notification_logs 
SET sent_at = NOW(), status = 'SENT', updated_at = NOW()
WHERE campaign_id = ? AND customer_id = ? AND execution_id = ?;

// Worker: 배송완료 콜백 처리
UPDATE notification_logs 
SET delivered_at = NOW(), updated_at = NOW()
WHERE campaign_id = ? AND customer_id = ? AND execution_id = ?;
```

**장점:** SELECT 없음, 단순 UPDATE만 → DB 부하 낮음.

#### 대안 1: 메모리 캐시 + 배치 업데이트 (권장 검토)

**상황:** 콜백 처리 전에 기존 행의 상태를 검증해야 하는 경우 (예: 중복 전달 방지, 상태 전이 유효성 검사)

```java
// Worker: 발송 이벤트를 메모리 큐에 적재
class NotificationEvent {
    Long notificationId;
    Long campaignId;
    Long customerId;
    Long executionId;
    String eventType; // SENT, DELIVERED, CLICKED
    Instant timestamp;
}

// 메모리 캐시 (Redis 또는 로컬 Map)
Map<String, NotificationEvent> pendingEvents = new ConcurrentHashMap<>(); // key = "campaignId:customerId:executionId"

// Worker: 콜백 수신 시 (SELECT 없음)
pendingEvents.computeIfPresent("1:100:10", (k, existing) -> {
    // 상태 전이 검증 (메모리에서)
    if (existing.eventType.equals("SENT") && eventType.equals("DELIVERED")) {
        return new NotificationEvent(...); // 상태 갱신
    }
    return existing; // 중복 또는 잘못된 전이 무시
});

// 배치 처리 (초 단위, 예: 10초마다)
@Scheduled(fixedRate = 10_000) // 10초
public void flushPendingEvents() {
    List<NotificationEvent> events = new ArrayList<>(pendingEvents.values());
    
    // 일괄 INSERT (DELETE 선택사항)
    if (events.isEmpty()) return;
    
    // Option A: 일괄 INSERT ... ON DUPLICATE KEY UPDATE
    batchInsertOrUpdate(events); // SQL로 10-100개씩 묶어 처리
    
    // Option B: DELETE + INSERT (원자성 보장 필요)
    batchDeleteThenInsert(events); // 트랜잭션으로 감싸기
    
    pendingEvents.clear();
}

private void batchInsertOrUpdate(List<NotificationEvent> events) {
    String sql = """
        INSERT INTO notification_logs 
        (project_id, campaign_id, execution_id, customer_id, sent_at, delivered_at, ..., updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ..., NOW())
        ON DUPLICATE KEY UPDATE
            delivered_at = COALESCE(VALUES(delivered_at), delivered_at),
            clicked_at = COALESCE(VALUES(clicked_at), clicked_at),
            status = 'DELIVERED',
            updated_at = NOW()
        """;
    // JdbcTemplate.batchUpdate() 또는 Spring Data Batch
}
```

**장점:**
- 메모리 상태 검증 → DB SELECT 부하 없음
- 배치 처리 → INSERT/UPDATE 네트워크 왕복 최소화 (10초에 1회)
- 이벤트 순서 보장 (메모리 큐 구조)

**단점:**
- 메모리 사용 (큐 크기 제한 필요, 예: max 100,000 이벤트)
- Worker 재시작 시 데이터 손실 (DLQ 이동 메커니즘 필요)
- 복잡도 증가

#### 대안 2: DELETE + INSERT (재처리 안전성)

**상황:** 멱등성이 중요하고 중복 처리 가능한 경우

```java
// Worker: 콜백 처리 (조회 없음)
// 메모리에 이벤트 누적 후 배치 처리
void flushEvents() {
    String sql = """
        INSERT INTO notification_logs_staging 
        (campaign_id, customer_id, execution_id, sent_at, ...)
        VALUES (?, ?, ?, ?, ...)
        """;
    // 스테이징 테이블에 INSERT (속도 우선)
    
    // 1시간마다 메인 테이블로 병합
    @Scheduled(cron = "0 0 * * * *") // 매 시간
    void mergeFromStaging() {
        // DELETE + INSERT 또는 MERGE 구문
        String merge = """
            INSERT INTO notification_logs 
            SELECT * FROM notification_logs_staging
            ON DUPLICATE KEY UPDATE ...
            """;
        // 트랜잭션 내에서 스테이징 테이블 TRUNCATE
    }
}
```

**장점:**
- 스테이징 테이블이 작아서 빠름
- 메인 테이블은 배치 시간에만 수정 → 동시성 문제 감소

**단점:**
- 스테이징 테이블 추가 관리
- 메인-스테이징 병합 중 지연 (수십 초~분)

---

## 최종 결정: Redis 캐시 + 배치 UPDATE

**선택 근거:**
1. **DB SELECT 완전 제거** — notification_logs는 대용량이므로 SELECT 빈도 최소화 필수
2. **배치 처리** — Redis에서 메모리 상태 검증 후 DB는 INSERT/UPDATE만 수행
3. **멱등성 보장** — `INSERT ... ON DUPLICATE KEY UPDATE`로 중복 자동 제어
4. **메모리 효율** — Redis TTL + 정기적 플러시로 OOM 방지
5. **분산 환경 대비** — 향후 Worker 다중 인스턴스 확장 시 Redis 공유 상태 활용 가능

### 구현 아키텍처

```
1. 발송 이벤트 (Kafka)
   ↓
2. Worker: 이벤트 파싱 → Redis에 임시 캐시
   key: "nlog:{projectId}:{campaignId}:{customerId}:{executionId}"
   value: {eventType, timestamp, status, ...}
   TTL: 24시간 (발송 완료까지 필요)
   ↓
3. 콜백 이벤트 (Kafka)
   ↓
4. Worker: Redis에서 기존 상태 조회 → 상태 전이 검증 → 메모리에서 갱신
   (DB SELECT 없음)
   ↓
5. 배치 스케줄러 (10초 주기)
   - Redis에서 모든 이벤트 읽기
   - 일괄 INSERT ... ON DUPLICATE KEY UPDATE
   - Redis에서 처리 완료한 이벤트 삭제
```

### 구현 체크리스트
- [ ] **Redis 스키마**
  - key: `nlog:{projectId}:{campaignId}:{customerId}:{executionId}` (유니크)
  - value: JSON `{eventType, sent_at, delivered_at, clicked_at, status, updated_at}`
  - TTL: 24시간
  - Max key 개수: 100,000 (~10MB)

- [ ] **상태 검증 로직 (메모리)**
  - SENT → DELIVERED / FAILED 허용
  - DELIVERED → CLICKED 허용
  - CLICKED → 상태 변경 금지
  - 상태 전이 위반 시: 로그 기록 후 무시 (중복 콜백)

- [ ] **배치 플러시 구현**
  - `@Scheduled(fixedRate = 10_000)` (10초)
  - 또는 조건부 플러시: 크기 > 1,000 또는 지연 > 5초
  - INSERT ... ON DUPLICATE KEY UPDATE 사용
  - 트랜잭션 내에서 처리 (원자성 보장)

- [ ] **Worker 종료 전 플러시**
  - `@PreDestroy` 메서드로 graceful shutdown
  - 남은 이벤트를 DB에 쓴 후 종료

- [ ] **큐 오버플로우 처리**
  - Redis 메모리 가득 찼을 때: Kafka DLQ로 재이동
  - 모니터링 알람: 큐 크기 > 80,000

- [ ] **모니터링 메트릭**
  - Redis 큐 현재 크기
  - 배치 플러시 지연 (평균, P99)
  - DB 배치 INSERT 크기 (이벤트/초)
  - 상태 전이 오류율 (중복 콜백)

### 추가 합의 사항 (역할 5 회의에서 확정)
- [x] **Redis 인스턴스** — **프로젝트 공유 Redis** (이미 존재) ✅
  - butterfly-reporting, butterfly-segment 등 이미 사용 중
  - 별도 인스턴스 신설 불필요
- [x] **메모리 제한** — **최대 100,000 이벤트** (MVP 충분) ✅
  - 약 10MB 메모리
  - 모니터링: 80,000 이상 시 알람
- [x] **배치 주기** — **10초 고정** (튜닝 기준 정의) ✅
  - 또는 조건: 크기 > 1,000 이벤트 즉시 플러시
  - 초기 모니터링 후 필요시 5초로 조정
- [x] **중복 검사 키** — **`{projectId}:{campaignId}:{customerId}:{executionId}`** ✅
  - execution_id로 회차 구분 (RECURRING 지원)
  - 유니크성 보장됨 (campaign + customer + execution)
- [x] **콜백 타임스탐프** — **Worker 수신 시각 (NOW())** ✅
  - SDK 타임스탐프는 서명 검증용만 사용
  - 배치 집계에는 sent_at (발송 시각) 기준

---

## Blocker #3: 발송 카운트 규칙 정의

### 현황
- 제안서 §1-3에서 "카운트 기준"을 언급했지만 합의되지 않음
- ADR-0003에서 timestamp 기반을 권장했으나 Worker 구현 기준과 확인 필요

### 문제: 상태 전이 구조
`notification_logs.status`는 **상태 머신**이다:

```
SENT → DELIVERED → CLICKED (선형 전이)
        → FAILED (언제든 실패 가능)
```

단순 status enum 카운트는 위험:
- "DELIVERED인 로그"를 세면 CLICKED 상태인 로그는 빠짐 → 중복 제거 문제
- 상태 전이 버그 시 카운트 불일치

### 제안: 타임스탬프 기반 (권장)

```sql
-- 집계 테이블에서 계산 (재계산 배치 SQL)
UPDATE campaign_metric_daily 
SET 
  sent_count = (
    SELECT COUNT(*) 
    FROM notification_logs 
    WHERE project_id = ? 
      AND campaign_id = ? 
      AND execution_id = ? 
      AND bucket_date = DATE(CONVERT_TZ(sent_at, '+00:00', '+09:00'))
      AND sent_at IS NOT NULL
  ),
  delivered_count = (
    SELECT COUNT(*) 
    FROM notification_logs 
    WHERE ... 
      AND delivered_at IS NOT NULL
  ),
  clicked_count = (
    SELECT COUNT(*) 
    FROM notification_logs 
    WHERE ... 
      AND clicked_at IS NOT NULL
  ),
  failed_count = (
    SELECT COUNT(*) 
    FROM notification_logs 
    WHERE ... 
      AND status = 'FAILED'
  ),
  ...
WHERE ...;
```

**장점:**
- 상태 머신 전이와 무관하게 독립적 계산
- 중복 카운트 없음
- 재계산 배치가 멱등 (동일 timestamp 행은 동일 카운트로 수렴)

### 대안: 상태 기반 (비권장)
```sql
SELECT COUNT(*) WHERE status = 'SENT'  -- 현재 상태가 SENT
```
→ DELIVERED 전이 시 카운트 변동 → 일일 집계 값이 매번 달라짐 (대시보드 혼동)

### 합의 항목
- [x] **타임스탐프 기반** (sent_at IS NOT NULL 등) 채택 ✅ 합의 완료
  - `sent_count = COUNT(*) WHERE sent_at IS NOT NULL`
  - `delivered_count = COUNT(*) WHERE delivered_at IS NOT NULL`
  - `clicked_count = COUNT(*) WHERE clicked_at IS NOT NULL`
  - `failed_count = COUNT(*) WHERE status = 'FAILED'`
- [x] 상태 머신 전이 검증 ✅ Redis 메모리에서 처리
- [x] 배치 지연: 10초 주기 (Redis 플러시) 합의 ✅

---

## Blocker #4: SDK 콜백 인증 강화 (서명 토큰)

### 현황
- 고객 SDK가 클릭/배송완료 콜백을 Worker로 전송: `POST /sdk/callbacks`
- 현재 제안서에서 "SDK 인증"이라 표기했으나 구체 메커니즘이 명시되지 않음
- 분석 데이터 신뢰성을 위해 콜백 검증 필수

### 문제: 위조 공격
1. **클릭 위조** — 누군가 `POST /sdk/callbacks?campaign_id=1&click=1`로 fake 콜백 보냄
   - 결과: 대시보드의 clickRate이 부풀려짐 → 분석 신뢰성 손상
2. **배송 완료 위조** — delivered_count 조작
3. **세그먼트 정확도** — 전환 기록 조작으로 세그먼트 재계산 오염

### 제안: HMAC 서명 토큰 (권장)

**구현 흐름:**

1. **SDK 초기화 시 공유 시크릿 설정**
   ```java
   // 고객 앱 측 (SDK)
   ButterflySDK.initialize(
     projectId = "...",
     apiKey = "...",
     sharedSecret = "..." // 서버에서 발급
   );
   ```

2. **콜백 발송 시 서명 생성**
   ```java
   // SDK 측
   String payload = "{\"campaign_id\": 1, \"event\": \"CLICKED\", \"timestamp\": ...}";
   String signature = HMAC_SHA256(payload, sharedSecret);
   
   POST /sdk/callbacks
   Body: payload
   Header: X-Signature: signature
   ```

3. **Worker 콜백 핸들러에서 검증**
   ```java
   @PostMapping("/sdk/callbacks")
   public ApiResponse<Void> receiveCallback(
       @RequestBody String payload,
       @RequestHeader("X-Signature") String clientSignature
   ) {
       String serverSignature = HMAC_SHA256(payload, sharedSecret);
       if (!MessageDigest.isEqual(serverSignature.getBytes(), clientSignature.getBytes())) {
           throw new BusinessException(ErrorCode.INVALID_CALLBACK_SIGNATURE);
       }
       // 처리...
   }
   ```

**장점:**
- 공개 엔드포인트에서도 위조 방지 (sharedSecret은 발급된 SDK 앱에만 알려짐)
- 경량 (JWT 검증보다 빠름)
- SDK와 Worker 간 신뢰 체인 구성

### 대안 고려
- **API Key + HMAC** — sharedSecret 외에 apiKey도 서명에 포함
- **타임스탬프 검증** — 서명 + 재전송 방지용 timestamp 윈도우 (±5분)

```java
// 강화 버전: 타임스탬프 포함
String payload = "{..., \"timestamp\": " + System.currentTimeMillis() + "}";
long callbackTime = jsonPayload.get("timestamp").asLong();
if (Math.abs(System.currentTimeMillis() - callbackTime) > 5 * 60 * 1000) {
    throw new BusinessException(ErrorCode.CALLBACK_TIMESTAMP_EXPIRED);
}
```

### 구현 위치
- **butterfly-notification** — NotificationCallbackController + CallbackSignatureValidator
- **butterfly-auth** — SharedSecret 발급/조회 로직 (고객 앱 생성 시)
- **butterfly-worker** — Kafka/RabbitMQ 소비자에서 이미 받은 콜백 재처리 없음 보장

### 합의 항목
- [x] **HMAC-SHA256 기반 서명** 채택 ✅ 합의 완료
- [x] sharedSecret 발급 방식: **프로젝트별 고정 시크릿** ✅ 합의 완료
  - 프로젝트 생성 시 UUID 기반 난수 생성
  - 프로젝트별 고정 (회전 미지원, MVP 단계)
- [x] 타임스탬프 검증: **YES, ±5분 윈도우** ✅ 합의 완료
  - 재전송 방지 (replay attack 방어)
  - Worker 시계 편차 허용
- [x] 서명 실패 시: **즉시 400 Bad Request** ✅ 합의 완료
  - DLQ 이동 없음 (콜백은 재전송 가능)
- [x] SDK 문서: 초기화 시 sharedSecret 설정 가이드 ✅ ADR-0006 + 별도 SDK 문서

---

## 회의 진행 순서

1. **Blocker #2** (7분) — `notification_logs.project_id` + 인덱스 2개 병행
   - 배치용: `(project_id, sent_at)`
   - API용: `(project_id, campaign_id, sent_at)`

2. **Blocker #3** (8분) — 타임스탐프 기반 발송 카운트 + Redis 캐시 배치 업데이트
   - 카운트 규칙: `sent_at / delivered_at / clicked_at IS NOT NULL`
   - Worker 최적화: Redis 메모리 캐시 + 10초 배치 플러시
   - SELECT 없음, 상태 전이 검증은 메모리에서

3. **Blocker #4** (10분) — SDK 콜백 HMAC-SHA256 + X-Signature 헤더
   - 서명 검증 로직: MessageDigest.isEqual()
   - 타임스탐프 윈도우 선택사항

4. **Worker 구현 계획** (5분) — 일정 및 테스트 전략
   - notification_logs 마이그레이션
   - Redis 캐시 스키마 정의
   - 배치 플러시 스케줄러 구현
   - E2E 테스트: 발송 → 콜백 → 집계 테이블

## 기대 산출물

- [ ] 3건 모두 합의 완료 + Worker 구현 일정 확정
- [ ] `backend/docs/adr/0006-delivery-log-notification-aggregation.md` 신규 작성
  - notification_logs 스키마 변경
  - Redis 캐시 + 배치 UPDATE 전략
  - 상태 전이 규칙
  - 모니터링 메트릭

- [ ] `backend/docs/erd.md` 업데이트
  - `notification_logs.project_id` + 인덱스 2개 추가

- [ ] `backend/docs/api/04-sdk-callbacks.md` 신규 (또는 05-delivery.md 통합)
  - 콜백 요청/응답 포맷
  - X-Signature 헤더 명시
  - HMAC-SHA256 검증 예시

- [ ] **다음 세션:** 분석 엔티티/레포/서비스 구현 착수 (Blocker 3건 해결 후)
