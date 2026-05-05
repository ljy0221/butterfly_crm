---
status: accepted
date: 2026-05-04
decision-makers: 역할 2 (고객/이벤트 수집)
consulted: 역할 3 (세그먼트), 역할 5 (분석/리포팅) — events.user_id 참조 영향
informed: 역할 3, 5 — 옵션 B는 다른 모듈 영향 0이라 사후 통보로 진행. 분석 측 익명 카운트 표시 정책은 미해결로 남김 (§미해결)
---

# ADR-0012: 익명 사용자 이벤트 처리 + devices external 식별자 — ERD 갱신

## Context and Problem Statement

ADR-0011(이벤트 수집 흐름)과 02-customer.md(API 명세)를 구현하던 중 ERD 정의와 명세서/SDK 요구사항이 두 곳에서 충돌함을 발견.

### 충돌 1 — devices에 external 식별자 부재

명세서 §1-1: SDK는 `deviceId: "dev_xyz789"` 형태의 **외부 기기 식별자(String)** 를 보냄.
ERD: `devices.device_id BIGINT AUTO_INCREMENT` — 내부 PK만 존재. external 식별자 컬럼 없음.

→ SDK가 보낸 String을 어디로 매핑할지 불가. (`users.external_id VARCHAR(255)`는 이미 정의되어 있는데 devices엔 없음 — 비대칭)

### 충돌 2 — 익명(로그인 전) 사용자 이벤트 처리 정책 부재

명세서 §1-1: "userId 로그인 전 NULL 가능" — 페이지뷰, 자동 트래킹 이벤트 등은 로그인 전에도 발생.
ERD:

- `events.user_id BIGINT NOT NULL`
- `devices.user_id BIGINT NOT NULL` (코멘트에 "로그인 전 NULL"이라 적혔으나 NOT NULL 제약과 모순)
- `event_daily_agg.user_id BIGINT NOT NULL` (PK 일부)

→ 익명 사용자 이벤트를 어떻게 적재할지 정책 부재. NULL 허용? placeholder anonymous user?

### 영향 범위

이 결정은 customer 모듈 단독으로 박을 수 없음:

- **segment 모듈**: `segment_members.user_id` 참조 (FK). 익명 user 정책에 따라 segment 매칭 로직 변경
- **analytics/reporting 모듈**: `event_daily_agg.user_id` (PK 일부). 익명 user는 분석 카운트에서 어떻게 다룰지

→ 팀 합의 필요한 ADR. 본 문서는 proposed 상태로 작성.

## Decision Drivers

- 명세서 P0 요구 (login/signup 전 이벤트 수집)
- 분석 정확성 (익명 vs 가입 유저 카운트 구분 가능해야)
- SDK 인터페이스 단순성 (external 식별자 String을 server-side ID로 변환하는 부담 회피)
- 기존 ERD 변경 범위 최소화 (다른 모듈 영향 작게)
- MVP 일정 (대형 마이그레이션 회피)

## Considered Options

### 옵션 A — external_id 컬럼 추가 + user_id NULL 허용

```sql
ALTER TABLE devices
    ADD COLUMN external_id VARCHAR(255) NOT NULL;
ALTER TABLE devices
    ADD CONSTRAINT UK_DEVICES_PROJECT_EXTID UNIQUE (project_id, external_id);
ALTER TABLE devices MODIFY COLUMN user_id BIGINT NULL;

ALTER TABLE events MODIFY COLUMN user_id BIGINT NULL;

-- event_daily_agg.user_id는 PK 일부라 NULL 불가 → 별 처리 (옵션 A-1 또는 A-2)
```

- **A-1**: `event_daily_agg`의 익명 행은 user_id에 sentinel 값 사용 (예: 0). PK 유지.
- **A-2**: `event_daily_agg` PK에서 user_id 제거 → `(log_date, project_id, category_id)`로 변경. 유저별 집계 능력
  손실.

### 옵션 B — external_id 컬럼만 추가, NOT NULL 유지 + anonymous placeholder user

```sql
ALTER TABLE devices
    ADD COLUMN external_id VARCHAR(255) NOT NULL;
ALTER TABLE devices
    ADD CONSTRAINT UK_DEVICES_PROJECT_EXTID UNIQUE (project_id, external_id);
-- user_id는 NOT NULL 유지
-- events.user_id, event_daily_agg.user_id 변경 0
```

각 프로젝트마다 익명 placeholder user 1개를 생성 (`users.external_id = '__anonymous__'` 같은 컨벤션). 익명 이벤트는 그
user_id로 적재.

### 옵션 C — ERD 유지, 명세서가 ERD 따르도록 갱신

- `deviceId/userId` String external_id 폐기 → SDK가 numeric ID 사용 (서버 발급 PK를 SDK가 저장 후 재사용)
- 익명 이벤트 자체를 비지원 — 로그인 전에는 이벤트 수집 안 함
- 명세서 수정본 갈아엎기

## Comparison

| 항목                        | A-1 (sentinel)                  | A-2 (PK 변경)                         | B (placeholder)                       | C (명세 갈아엎기)   |
|---------------------------|---------------------------------|-------------------------------------|---------------------------------------|---------------|
| ERD 변경 범위                 | devices/events nullable + 컬럼    | devices/events nullable + agg PK 변경 | devices 컬럼만                           | 0 (DB)        |
| 명세서 호환                    | ✅                               | ✅                                   | ⚠️ (anonymous user 약속 필요)             | ❌             |
| 익명 vs 가입 user 구분          | ✅ (NULL/sentinel로 구분)           | ✅ (NULL로 구분)                        | ⚠️ (placeholder user 1개라 디바이스별 구분 불가) | N/A           |
| 유저별 집계                    | ✅                               | ❌ (PK에서 user_id 제거)                 | ✅ (placeholder는 한 row로 묶임)            | N/A           |
| FK 영향 (segment_members 등) | NULL 허용 변경                      | NULL 허용 변경                          | 0                                     | 0             |
| 익명 이벤트 카운트 정확성            | sentinel 0의 event_count → 익명 총합 | 카테고리 일자 총합만 (유저별 X)                 | placeholder 한 user의 카운트로 누적 (유저 분리 X) | 익명 이벤트 자체 X   |
| SDK 인터페이스                 | external_id String              | external_id String                  | external_id String                    | numeric ID 직접 |
| 분석 narrative              | "익명/가입 분리 추적 가능"                | "유저별 분석 능력 손실"                      | "익명 placeholder 패턴"                   | "로그인 후만 추적"   |
| 마이그레이션 부담                 | 중 (스키마 변경 + 기존 데이터 변환)          | 중-대 (PK 변경)                         | 작음 (컬럼 추가만)                           | 작음 (코드만)      |
| MVP 적합도                   | 중                               | 낮음                                  | 높음                                    | 낮음 (요구 미충족)   |

## Decision Outcome

**채택**: 옵션 B (devices.external_id 컬럼 추가 + anonymous placeholder user)

근거 raw 메모 (본인 언어로 다시 쓸 것):

- ERD 변경 가장 작음. devices에 컬럼 1개만 추가. events/agg는 변경 0
- segment_members, analytics 같은 다른 모듈 영향 0 (NOT NULL 유지) → consulted 역할 3/5에 추가 부담 0이라 사후 통보로 충분하다고
  판단
- placeholder user 패턴은 분석/세그먼트 모듈에서 익명 카운트를 별 user_id로 인지하면 됨 (filter or split)
- "디바이스별 익명 추적"이 필요해지면 그때 옵션 A로 마이그레이션 가능 (점진적)

옵션 A의 매력 (익명/가입 정확 분리)은 시인되지만:

- segment, analytics 모듈에 NULL 처리 코드 추가 비용 큼
- event_daily_agg PK 변경(A-2)은 유저별 집계 능력 손실 — 분석 가치 큰 손실
- A-1의 sentinel 0은 hack 느낌. 명시적 placeholder user(B)가 의미론적으로 더 명확

옵션 C는 명세서 P0 요구 (로그인 전 이벤트 수집)와 정면 충돌 → 채택 불가

### 미해결 § 결정 (옵션 B 세부)

기존 "미해결" 항목 중 customer 모듈 내부 결정 2개는 본 ADR에서 함께 박는다. 역할 5 영역 1개는 미해결 유지.

**1. placeholder user external_id 컨벤션 → `__anonymous__`**

- 전역 reserved 값. (project_id, external_id) UNIQUE 제약이라 프로젝트당 1개씩 생성 가능
- 밑줄 2개 prefix는 reserved 컨벤션 (Python double-underscore와 같은 의도) — 일반 SDK 클라이언트가 의도적으로 이 값을 보낼 가능성 사실상
  0
- 대안 `_anon_<projectId>`는 project 단위 컨벤션이긴 하나, 어차피 UNIQUE 제약 자체가 (project_id, …) 조합이라 굳이 식별자에
  projectId를 박을 이유 없음

**2. placeholder user 생성 시점 → lazy lookup-or-create**

- 첫 익명 이벤트 진입 시 `(projectId, '__anonymous__')` lookup, 없으면 생성
- 이유: 일반 user/device에도 (projectId, external_id) lookup-or-create 코드가 어차피 필요하므로 같은 경로에 placeholder
  케이스를 동일 로직으로 처리 가능
- 대안 "프로젝트 생성 hook"은 project 도메인이 customer 도메인을 호출하는 역방향 의존을 만들어 회피
- 동시성: 같은 (projectId, external_id) UNIQUE 제약을 신뢰. 첫 insert race는 unique 충돌 catch 후 재조회로 처리

**3. analytics 측 익명 user 카운트 표시 정책 → 미해결**

- 역할 5 결정 영역. customer 모듈은 placeholder user_id 적재까지만 책임짐
- 분석/리포팅에서 placeholder를 일반 user 합계와 분리 표시할지, 합산할지, 별도 KPI로 뽑을지는 역할 5가 별 ADR로 박아야 함
- customer 측은 "external_id = `__anonymous__`인 user는 placeholder"라는 사실만 약속

### ERD 사실 확인 (2026-05-04 ERDCloud 덤프 기반)

옵션 B 채택 후 실제 ERD 상태와 대조 결과:

**B 채택을 강화하는 사실:**

- `users.external_id VARCHAR(255) NOT NULL` 이미 존재 → users 테이블 컬럼 작업 0
- `users.user_id`를 FK로 참조하는 테이블 = `event_daily_agg, segment_members, segment_snapshot_users` 3개.
  placeholder user는 정상 user row이므로 FK 만족 → 다른 모듈 스키마 변경 0 주장 그대로 성립
- `event_daily_agg` PK = `(log_date, project_id, category_id, user_id)` 확인. user_id NULL 불가 → 옵션 A
  계열은 더더욱 큰 부담임이 재확인됨

**B 채택해도 손봐야 하는 항목 (Implementation Checklist 갱신 사항):**

- ~~`users (project_id, external_id) UNIQUE` 제약 부재~~ → **2026-05-04 정정**: erd.md 537줄에
  `UK_USERS_PROJECT_EXTID`로 이미 박혀있음. ERDCloud 덤프가 export 시 빠뜨린 것이지 ERD 자체엔 존재. 추가 작업 불필요
- `events`에 explicit FK 정의가 ERDCloud 덤프에 없음 (다른 테이블엔 있음) — 옵션 B에 영향 없으나 사실 기록. erd.md 575~577줄에는
  정의되어 있음 (events FK_user, FK_category, FK_device 모두 명시) → 마찬가지로 ERDCloud export 누락
- `devices.user_id`, `events.user_id` 모두 `NOT NULL`인데 코멘트엔 "로그인 전 NULL" — Context에서 짚은 모순이 ERD 그대로
  박혀있음 재확인. 옵션 B는 placeholder user_id로 채워 NOT NULL 유지

**ERD 진실 공급원**: erd.md > ERDCloud 덤프. ERDCloud export는 unique constraint, FK 등 일부를 빠뜨리는 경향이 있어 사실
확인은 erd.md 우선.

### 옵션 B의 의미론적 영향 (스키마 변경 0이라고 해서 영향 0은 아님)

`segment_members.user_id` FK가 `users`를 가리키므로, **placeholder user도 segment 매칭 후보로 들어갈 수 있다.**

예시:

- 운영자가 "최근 30일 이벤트 발생자" 세그먼트 정의
- segment 빌드는 events 테이블 또는 event_daily_agg를 스캔해 user_id 추출
- placeholder user는 익명 이벤트가 누적된 한 row → 한 user로 매칭됨

이건 의도와 다를 수 있다. customer 측에선 막을 수 없고 segment 빌드 로직에서 placeholder 제외 정책이 필요할 수 있음. 역할 3 사후 통보 시 명시적으로
짚어야 할 항목 (Implementation Checklist 반영).

## Consequences

### 좋은 점 (옵션 B 기준)

- ERD 변경 1개 컬럼 (devices.external_id) + 1개 UNIQUE 제약. 마이그레이션 부담 최소
- 다른 모듈 (segment, analytics) 변경 0
- 익명 이벤트 적재 가능 (placeholder user_id로)
- SDK 인터페이스 명세서 그대로 (deviceId: String, userId: String)

### 나쁜 점 / 주의

- placeholder user 1개에 모든 익명 이벤트가 누적됨 → 분석 시 "익명 user 1명이 1만 이벤트 발생" 형태로 보임. 디바이스별 익명 추적 불가
- placeholder user의 external_id 컨벤션 결정 필요 (`__anonymous__` 같은 reserved 값. 충돌 방지)
- 프로젝트 생성 시 placeholder user 자동 생성 hook 필요 (또는 첫 익명 이벤트 시 lazy 생성)
- 추후 디바이스별 추적이 필요해지면 옵션 A로 마이그레이션 (event_daily_agg 재계산 비용)

### 미해결

- ~~placeholder user의 external_id 컨벤션~~ → `__anonymous__` (Decision Outcome §미해결-결정 1)
- ~~placeholder user 생성 시점~~ → lazy lookup-or-create (Decision Outcome §미해결-결정 2)
- analytics 측에서 익명 user의 event_count를 일반 user 합계와 분리 표시할 정책 → **역할 5 결정 대기** (별 ADR 예상)
  external_id

## Implementation Checklist

ERD 변경 (Flyway 영역 — 본인이 별도 작업):

- [ ] `devices` 테이블에 `external_id VARCHAR(255) NOT NULL` 컬럼 추가
- [ ] `devices (project_id, external_id) UNIQUE` 제약 추가
- [x] ~~`users (project_id, external_id) UNIQUE` 제약 추가~~ — **erd.md 537줄에 이미 박혀있음** (
  `UK_USERS_PROJECT_EXTID`). ERDCloud 덤프가 export 시 빠뜨린 것. 별도 작업 불필요
- [ ] (선택) `events.user_id`, `devices.user_id` 컬럼 코멘트 정정 — "로그인 전 NULL" 문구 제거 (옵션 B에선 placeholder
  user_id가 채움)

결정 (본 ADR에서 박음):

- [x] anonymous placeholder user 컨벤션 → `__anonymous__` (§미해결-결정 1)
- [x] placeholder 생성 시점 → lazy lookup-or-create (§미해결-결정 2)

코드 (event 모듈):

- [x] `User`, `Device` 엔티티 + Repository 신설 (ADR-0011은 customer 모듈로 표현했으나 실제 분리는 butterfly-event)
- [ ] `EventCollectService`에서:
    - user: (projectId, external_id) **lookup-or-create** — request.userId가 null이면
      `User.PLACEHOLDER_EXTERNAL_ID`로 대체. unique 충돌 catch+재조회로 race 처리
    - device: (projectId, external_id) **lookup-or-throw** — `DEVICE_NOT_REGISTERED 404`. device 등록은
      별도 endpoint(API 19) 책임
- [ ] DTO 변경: `EventCollectRequest.userId/deviceId` Long → String (external_id). userId는 nullable(익명
  가능), deviceId는 `@NotBlank`. **EventPayload는 Long 유지** (service가 lookup 후 internal PK를 박음)

device가 lookup-or-throw인 이유 (NOT NULL ERD 유지 결정 — 2026-05-04 보강):

- ERD `devices.push_token`, `push_token_updated_at`, `platform`, `app_version`, `os_version` 모두 NOT
  NULL
- events 페이로드만으로는 device row 생성 불가 (필수 필드 부재)
- → device 등록은 SDK init 시점에 별도 endpoint(API 19)로 분리. events는 등록된 device를 참조만 함
- 푸시 미동의/토큰 미발급 device는 `push_token = ""` (빈 문자열 sentinel) + `token_valid = TRUE`로 등록. 푸시 발송 로직이
  `push_token=''`을 발송 대상에서 제외하면 도메인 의미 유지됨 (NULL 허용보다 명시적)

소통:

- [x] 02-customer.md 갱신 (2026-05-04) — API 19 request에 `deviceId` (String external_id), `userId` (
  String, nullable), push_token sentinel(`""`) 명시. response에서 internal `deviceId` Long 제거 / API 20
  userId/deviceId Long → String, `DEVICE_NOT_REGISTERED 404` 에러 코드 추가
- [ ] 역할 3 (세그먼트) 사후 통보 — 스키마 변경 0이지만 **placeholder user가 segment_members 매칭 후보로 들어갈 수 있음** 명시 (
  segment 빌드 시 제외 정책 검토 필요)
- [ ] 역할 5 (분석) 별 ADR 요청 — 익명 카운트 표시 정책

## More Information

- 관련 ADR: [ADR-0011 이벤트 수집 흐름](0011-event-collection-flow.md)
- 관련 명세: [02-customer.md §1-1](../api/02-customer.md)
- 개인 ADR: [ADR-0001 이벤트 멱등 키](../../../docs/decisions/0001-이벤트멱등키-uuid별도컬럼.md)
