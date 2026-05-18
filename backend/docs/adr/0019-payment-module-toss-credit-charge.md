---
status: proposed
date: 2026-05-15
decision-makers: 결제 도메인 담당
consulted: 역할 1 (auth/tenant), 역할 6 (분석/통합 검증)
related: ADR-0005, ADR-0013, ADR-0014
---

# ADR-0019: 결제 모듈 도입 — 토스페이먼츠 v2 기반 프로젝트 단위 크레딧 충전

## Context and Problem Statement

Butterfly는 멀티테넌트 CRM SaaS로 프로젝트(테넌트) 단위 유료 사용량 정산이 필요하다.
초기 과금 모델은 **크레딧(포인트) 선불 충전 방식**:
프로젝트 OWNER가 카드/간편결제로 충전 → 프로젝트 잔액에 적립 →
캠페인 실행 전 예상 발송량만큼 예약 → 실제 성공 발송량 기준으로 정산한다.

이 ADR은 신규 도메인 모듈 `butterfly-payment` 의 경계, PG 통합, DB 스키마, 인가, 멱등성,
크레딧 예약/정산 계약을 정한다.

## Decision Drivers

- 한국 시장 SaaS → **국내 PG 필수** (Toss/Iamport/KakaoPay)
- 모듈 경계(ADR-0014) 준수 — 결제 도메인은 다른 도메인 내부에 침투 X
- 멀티테넌시 — 잔액은 **프로젝트 단위**, 멤버는 결제 actor 로만 기록
- 멱등성 — 외부 PG 호출은 재시도·중복 confirm 에 안전해야 함
- 감사성 — 모든 잔액 변동은 원장(ledger) 에 append-only 로 기록

## Considered Options

### 1. PG사 선택

| 옵션 | 장점 | 단점 | 결정 |
|---|---|---|---|
| **A. Toss Payments v2** | 국내 점유율 1위, v2 SDK 가 결제수단 통합 추상화, 카드/간편결제/가상계좌/현금영수증 모두 단일 API | 마이너 PG보다 수수료 약간 높음 | ✅ 채택 |
| B. 아임포트(포트원) | 다중 PG 추상화 | 추상화 레이어가 한 겹 더 — 우리는 단일 PG로 충분 | ❌ 과함 |
| C. Stripe | 글로벌 표준 | 국내 카드 직승인 제약, 한국어 영수증/현금영수증 미지원 | ❌ |

### 2. 잔액 소유 주체

| 옵션 | 결정 |
|---|---|
| **A. 프로젝트 단위 (`credit_account.project_id` PK)** | ✅ 채택 — SaaS 과금 주체가 테넌트(프로젝트)이며 멤버 단위 잔액은 의미 없음 |
| B. 멤버 단위 | ❌ 같은 프로젝트 멤버 간 잔액 분리는 사용성 저해 |

### 3. 잔액 일관성 모델

| 옵션 | 장점 | 단점 | 결정 |
|---|---|---|---|
| **A. 현재 잔액 row + ledger** (`credit_account` + `credit_transaction`) | 조회 O(1), 직관적, 검증은 ledger SUM 으로 가능 | 두 곳 일관성 관리 필요 | ✅ 채택 — UNIQUE 제약 + 단일 트랜잭션 서비스 통과 강제 |
| B. 완전 이벤트소싱 | 감사성 최고 | 구현 복잡도 과함 (현 단계 over-engineering) | ❌ Post-MVP |

### 4. orderId 생성 주체

| 옵션 | 결정 |
|---|---|
| A. 클라이언트 UUID | ❌ 위변조 가능 |
| **B. 백엔드 생성** | ✅ 채택 — `POST /payments/orders` 에서 서버가 orderId 발급. 클라이언트는 발급된 orderId 로 Toss 결제창 호출 |

## Decision Outcome

### 모듈 구조

```
butterfly-payment/
└── src/main
    └── java/com/b101/butterfly/payment/
        ├── config/            # PaymentConfig, TossPaymentsProperties
        ├── presentation/      # 컨트롤러 + dto
        ├── application/       # 서비스 + command/result
        ├── domain/            # 엔티티 + enums
        ├── port/              # PaymentGatewayPort (Hexagonal 인바운드 포트)
        ├── infrastructure/    # TossPaymentsAdapter, JPA 리포지토리
        └── exception/code/    # PaymentErrorCode
```

`butterfly-api/build.gradle` 에 `implementation project(':butterfly-payment')` 추가, `settings.gradle` 에 모듈 등록.

### DB 스키마 (V19)

- `payment_order` — 결제 주문/거래 (PENDING → CONFIRMED/FAILED/CANCELLED). 환불용 컬럼(`cancel_amount`, `cancelled_at`) 미리 정의.
- `credit_account` — 프로젝트 1행, `balance BIGINT CHECK(balance>=0)`, `version BIGINT` (낙관 락).
- `credit_transaction` — 모든 잔액 변동의 append-only 원장. `UNIQUE(payment_order_id, reason)` 로 중복 적립 차단.

ADR-0013 §4 는 도메인 모듈 자기 자리에 두기를 권장하지만, develop 현 시점 모든 마이그레이션이 `butterfly-api/src/main/resources/db/migration/` 에 집중되어 있다. 본 PR 도 **기존 관행을 따라 같은 디렉토리에 V19 부터 추가**한다 (번호 순서·충돌 감지 용이성 우선).

### 크레딧 예약/정산 스키마 (V20)

- `credit_account.reserved_balance` — 아직 실제 차감되지는 않았지만 캠페인 실행이 선점한 금액. `availableBalance = balance - reservedBalance`.
- `credit_reservation` — `campaign_execution_id` 단위 예약 상태. 같은 실행 회차는 한 번만 예약된다.
- `credit_transaction.reference_type/reference_id` — 충전 주문 외에도 캠페인 실행 같은 사용량 원인을 추적하기 위한 참조 키.

`credit_account` 는 `reserved_balance >= 0`, `reserved_balance <= balance` 를 DB 제약으로 보장한다.
동일 프로젝트에서 여러 캠페인이 동시에 시작되어도 `credit_account` row 를 `SELECT FOR UPDATE` 로 잠근 뒤
사용 가능 잔액만 예약하므로 다른 실행이 잡아 둔 예약분을 침범하지 않는다.

### REST API (1차 PR)

- `POST /api/v1/projects/{projectId}/payments/orders` — 충전 주문 생성, orderId 반환
- `POST /api/v1/projects/{projectId}/payments/confirm` — `{orderId, paymentKey, amount}` 승인 처리
- `GET  /api/v1/projects/{projectId}/credits/balance` — 잔액 조회
- `GET  /api/v1/projects/{projectId}/credits/transactions` — 원장 페이징 조회

모두 `@PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")`. memberId 는 SecurityContext 의 `Authentication#getName()` 에서 추출(ADR-0005 컨벤션).

### 캠페인 실행 크레딧 계약

캠페인 발송은 세그먼트 미리보기/발송 대상 산출 결과를 기준으로 먼저 예약하고, 실제 발송 결과 이벤트로 정산한다.

1. 발송 직전 예약: `reserveForCampaignExecution(projectId, campaignId, campaignExecutionId, expectedCount, unitPrice)`
   - `expectedCount * unitPrice` 만큼 `reserved_balance` 에 더한다.
   - 같은 `campaignExecutionId` 로 이미 예약된 경우 멱등 반환한다.
   - `availableBalance` 가 부족하면 `PAY_INSUFFICIENT_BALANCE` 를 던지고 예약/발송을 시작하지 않는다.
2. 발송 결과 정산: `settleCampaignExecution(campaignExecutionId, sentCount)`
   - 예약이 없거나 이미 닫힌 예약이면 멱등 반환한다.
   - `sentCount == 0` 이면 실제 차감 없이 예약만 해제한다.
   - 실제 발송량이 예상보다 적으면 성공 발송분만 `SPEND` 원장에 기록하고 나머지 예약분을 해제한다.
   - 실제 발송량이 예상보다 많으면 같은 단가로 초과분을 `availableBalance` 에서 추가 차감한다.
   - 초과분을 충당할 사용 가능 잔액이 없으면 `PAY_INSUFFICIENT_BALANCE` 로 실패한다.

캠페인 `PAUSED/STOPPED` 판단, chunk 단위 중간 중단, 재개 정책은 캠페인 실행 도메인의 책임이다.
결제 모듈은 실행 회차별 예약/정산의 원자성, 멱등성, 감사 추적을 제공한다.

### Toss Payments 통합 — Port + Adapter

- **포트** `PaymentGatewayPort` — `confirm(orderId, paymentKey, amount)` 단일 메서드.
- **어댑터** `TossPaymentsAdapter` — Spring `RestClient` 로 `POST /v1/payments/confirm` 호출.
  - Authorization: `Basic base64(secretKey + ":")` — 콜론 누락 흔한 버그, 유닛 테스트로 검증
  - `Idempotency-Key` 헤더에 `orderId` 사용 (Toss 공식 권장)
  - 응답 status == `DONE` 인 경우만 잔액 적립. `WAITING_FOR_DEPOSIT`(가상계좌) 는 1차 PR 미지원 — 결제 수단을 카드/간편결제로 제한

### 멱등성 & 동시성

- DB 단:
  - `payment_order.order_id` UNIQUE
  - `credit_transaction(payment_order_id, reason)` UNIQUE → 같은 주문의 중복 CHARGE 차단
- 외부 호출 단:
  - Toss `Idempotency-Key` 헤더 사용 → Toss 측에서도 중복 confirm 차단
- 트랜잭션 경계:
  - **Toss 호출은 트랜잭션 바깥**에서 한다. 호출 결과 받은 직후 짧은 `@Transactional` 안에서 잔액 갱신 + ledger insert
  - `credit_account` 갱신은 row 단위 `SELECT ... FOR UPDATE` 또는 낙관 락(`@Version`) 둘 중 하나로 보호. 1차 PR은 비관 락(단순) 선택, 부하 증가 시 낙관 락으로 전환

### 금액 검증

- 클라이언트가 보내는 `amount` 는 **신뢰하지 않는다.** `confirm` 호출 전 `payment_order.amount` 와 일치 검증. 불일치 시 `PAY_AMOUNT_MISMATCH` 로 거절.
- 충전 금액은 화이트리스트 정액 티어: **10,000 / 30,000 / 50,000 / 100,000 / 300,000 (KRW)**. 주문 생성 단계에서 enum 검증.

### 권한 정책

- 결제 관련 API 는 **OWNER 전용**. EDITOR/VIEWER 는 잔액 조회도 거부 (운영 정책 — 변경 가능).

### 환경 변수

`butterfly-api/src/main/resources/application.yml` 에 추가:

```yaml
butterfly:
  payment:
    toss:
      client-key: ${TOSS_CLIENT_KEY:}
      secret-key: ${TOSS_SECRET_KEY:}
      base-url: ${TOSS_BASE_URL:https://api.tosspayments.com}
```

`@ConfigurationProperties("butterfly.payment.toss")` record 로 바인딩. 시크릿 키는 운영 시 K8s Secret 으로 주입.

### Phase 분할

| PR | 범위 |
|---|---|
| **PR1 (본 PR)** | 본 ADR + V19/V20 스키마 + 모듈 스켈레톤 + 주문 생성/카드·간편 confirm + 잔액/원장 조회 + 캠페인 실행 크레딧 예약/정산 + 프론트 충전 화면 3종 + 단위/통합 테스트 |
| PR2 | 환불·부분취소 API + 가상계좌 + Toss 웹훅(`DEPOSIT_CALLBACK`, `PAYMENT_STATUS_CHANGED`) |
| PR3 (선택) | ArchUnit 경계 규칙, 관리자용 결제 조회, 캠페인 외 사용량 차감 연동 |

## Consequences

### 좋은 점

- 결제 도메인이 단일 모듈로 격리 — 다른 도메인은 결제를 알 필요 없음
- DDD-라이트 헥사고날 — Toss → 다른 PG 교체 시 어댑터만 교체
- 모든 잔액 변동이 원장에 남아 회계·감사 추적 가능
- 멱등성이 DB·HTTP 양쪽에서 보장 — Toss 측 재시도, 클라이언트 자동 재시도 모두 안전

### 나쁜 점 / 주의

- 가상계좌 미지원 — 일부 사용자 결제 옵션 제약. PR2 까지 카드/간편결제만 안내 필요
- 환불 API 부재 — PR2 까지 운영자가 Toss 콘솔에서 수동 환불 (그래도 ledger 에는 ADJUST 로 보정 가능하도록 스키마는 준비)
- `credit_account` row 단위 락 — 동일 프로젝트의 동시 충전이 직렬화됨. SaaS 정상 사용 패턴에서는 영향 없음 (한 프로젝트가 초당 N건 충전하는 경우 없음)

### Post-MVP

- 가상계좌·휴대폰 결제 등 비동기 결제수단 지원 → 웹훅 도입 (PR2)
- 캠페인 외 사용량 기반 차감 — 세그먼트 빌드 등 다른 사용량 도메인의 예약/정산 계약 확장
- 잔액 임계치 알림 (notification 모듈 연동)
- 부분 환불·정산 보고서

## More Information

- ADR-0005 — `@PreAuthorize("@projectPermission.hasRole(#projectId, ROLE)")` 인가 컨벤션
- ADR-0013 — Flyway 마이그레이션 도입·번호 컨벤션
- ADR-0014 — 모듈 경계 / SPI 패턴
- Toss Payments v2 공식 문서: <https://docs.tosspayments.com/guides/v2/get-started/llms-quick-reference>
