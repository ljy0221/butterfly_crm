# Butterfly KPI 지표 정의서 & Attribution 기준

> Owner: 이재영
> 최초 작성: 2026-05-04
> 관련 Jira: `S14P31B101-76`
> 관련 ADR: [ADR-0007](../adr/0007-analytics-aggregation-tables-v2.md)
> 참조 스펙: [butterfly-reporting/SCHEMA_DESIGN.md](../../butterfly-reporting/docs/SCHEMA_DESIGN.md), [butterfly-reporting/butterfly_api_spec_v2.md](../../butterfly-reporting/docs/butterfly_api_spec_v2.md)

본 문서는 대시보드·리포트·집계 배치·API 응답이 **모두 같은 정의로 같은 숫자**를 보도록 KPI 계산 규칙을 고정한다. ADR-0007이 카운트와 비율 공식의 결정 근거를 담고 있다면, 본 문서는 그 결정을 구현·운영 측에서 참조하는 표준 문서다.

수정 시 ADR과 본 문서를 함께 갱신한다 — 한쪽만 바꾸면 팀마다 다른 숫자를 보게 된다.

---

## 1. 용어

| 용어 | 정의 |
|---|---|
| **발송(send)** | 발송 워커가 채널 게이트웨이에 전송을 시도해 `notification_logs` row가 생성된 시점. status 무관. |
| **도달(deliver)** | 채널 게이트웨이가 단말까지 전달을 confirm한 시점. `notification_logs.status='DELIVERED'`. |
| **오픈(open)** | 사용자가 메시지를 연 시점. `user_action_logs.action_type='OPEN'`. |
| **클릭(click)** | 사용자가 메시지 내 링크/CTA를 누른 시점. `user_action_logs.action_type='CLICK'`. |
| **전환(convert)** | 캠페인 의도 행동(구매/가입 등)이 완료된 시점. `user_action_logs.action_type='CONVERT'`. |
| **무시(dismiss)** | 사용자가 메시지를 명시적으로 닫거나 거부한 시점. `user_action_logs.action_type='DISMISS'`. KPI 분자에서는 사용하지 않으나 분석에 활용. |
| **회차(execution)** | 반복 캠페인의 1회 실행. `campaign_executions.campaign_execution_id`로 식별. 단발 캠페인은 NULL 허용. |
| **버킷(bucket)** | 집계 윈도우. `bucket_hour` (시간 단위) / `bucket_date` (일 단위 KST 자정 기준). |

**원칙**: 본 정의서가 ADR-0007과 충돌하면 ADR을 우선한다. 새 결정이 필요하면 ADR을 먼저 갱신하고 본 문서를 동기화한다.

---

## 2. 카운트 정의 (counter metrics)

모든 카운트는 **buckets × dimensions** 단위로 집계되며, 자연 키 UPSERT로 멱등성을 보장한다 ([ADR-0007](../adr/0007-analytics-aggregation-tables-v2.md)).

### 2.1 발송 단계 카운트 (`notification_logs` 기반)

| 카운트 | 정의 | SQL 의사코드 |
|---|---|---|
| `sent_count` | "발송 성공으로 채널 게이트웨이가 받은 건수" | `COUNT(*) WHERE status IN ('SENT','DELIVERED')` |
| `delivered_count` | "단말까지 도달이 confirm된 건수" | `COUNT(*) WHERE status = 'DELIVERED'` |
| `failed_count` | "발송 실패로 끝난 건수" | `COUNT(*) WHERE status = 'FAILED'` |
| `pending_count` | "아직 게이트웨이 응답을 못 받은 건수 — 운영 모니터링용" | `COUNT(*) WHERE status = 'PENDING'` |

**주의**: `sent_count`는 DELIVERED를 포함한다. "게이트웨이 단계까지 성공한 모집단" 의미라 일관성 있게 둔다 — DELIVERED는 SENT의 후속 상태이므로 SENT를 거쳐 갔다.

### 2.2 사용자 반응 카운트 (`user_action_logs` 기반)

| 카운트 | 정의 | SQL 의사코드 |
|---|---|---|
| `open_count` | OPEN 이벤트 총수 | `COUNT(*) WHERE action_type = 'OPEN'` |
| `click_count` | CLICK 이벤트 총수 | `COUNT(*) WHERE action_type = 'CLICK'` |
| `convert_count` | CONVERT 이벤트 총수 | `COUNT(*) WHERE action_type = 'CONVERT'` |
| `dismiss_count` | DISMISS 이벤트 총수 | `COUNT(*) WHERE action_type = 'DISMISS'` |

### 2.3 unique vs total

기본 카운트는 **total**(이벤트 row 수). 분석에서 "고유 사용자 수"가 필요한 경우 `_unique` 접미사를 붙여 별도 컬럼/응답 필드로 제공한다.

| total | unique |
|---|---|
| `open_count` = `COUNT(*)` | `open_unique` = `COUNT(DISTINCT user_id)` |
| `click_count` = `COUNT(*)` | `click_unique` = `COUNT(DISTINCT user_id)` |
| `convert_count` = `COUNT(*)` | `convert_unique` = `COUNT(DISTINCT user_id)` |

집계 테이블 5종(ADR-0007)은 v1에서는 **total만 보유**하고, unique는 후속 요구가 들어오면 컬럼 추가 + 재집계로 도입한다. v1 비율 지표는 모두 total 기반이다.

`user_id IS NULL`인 익명 이벤트는 unique 계산에서 **각 row를 별개로 취급하지 않고 제외**한다. 익명 분석은 다른 식별자(device_id 등)로 별도 지표를 만들 때 다룬다.

### 2.4 회차·캠페인 식별 일관성

집계 시 **다음을 한 단위(row)로 묶지 않는다**:
- 한 캠페인의 서로 다른 회차(`campaign_execution_id` 다름) — `campaign_stats_hourly`/`campaign_stats_daily`는 캠페인 단위이고 회차 드릴다운은 `notification_logs` 원장에서 본다 (ADR-0007 결정).
- 같은 캠페인의 PUSH/EMAIL — `channel`이 자연 키에 포함된다.

회차 단위 비교가 필요한 분석 화면은 `campaign_stats_hourly`를 회차의 시간 윈도우로 슬라이스해서 본다.

---

## 3. 비율 지표 (rate metrics)

### 3.1 표준 공식

```
delivery_rate = delivered_count / sent_count       × 100
open_rate     = open_count      / delivered_count  × 100
click_rate    = click_count     / open_count       × 100
convert_rate  = convert_count   / sent_count       × 100
```

**분모가 0이면 NULL** (0%로 표시하면 "0%인지 데이터 부재인지" 구분 불가). API 응답에서도 `null`로 내려보내고 UI에서 "—" 등으로 표기한다.

### 3.2 click_rate 분모를 open으로 둔 이유

이메일 업계 표준은 `click / delivered`(CTR)를 쓰지만 푸시·앱 메시지는 "사용자가 본 후 행동했는가"가 더 의미 있다. 본 시스템은 다채널이고 푸시 비중이 큼 — `click / open` 표준을 채택한다 (ADR-0007 §"비율 지표 공식 (v2)").

이메일 전용 분석에서 CTR이 필요하면 응답에 별도 필드로 추가한다 — 표준 비율을 변경하지 않는다.

### 3.3 매출 기여도 (revenue attribution)

매출 기여 지표는 v1에서는 다음만 확정한다 — 정교한 multi-touch attribution은 v2 이후에 검토.

| 지표 | 정의 |
|---|---|
| `attributed_revenue` | 본 캠페인이 last-click attribution으로 받은 매출 합계 (3.4의 윈도우 안에서) |
| `revenue_per_message` | `attributed_revenue / sent_count` |
| `roi` | `(attributed_revenue - cost) / cost`. cost 정의는 외부 시스템(빌링) 의존이므로 v1 미산출, 응답 필드만 둠 |

매출 데이터는 외부 이벤트(`CONVERT` payload의 `revenue` 필드)로 들어오며 본 모듈은 합산만 한다. 정확한 정의는 외부 팀과 합의 후 추가 ADR로 확정한다.

---

## 4. Attribution 기준

attribution은 "어떤 캠페인이 사용자의 행동을 유발했는가"를 결정하는 규칙이다. 같은 사용자가 여러 캠페인을 받은 경우 어느 쪽에 기여를 줄지 결정해야 한다.

### 4.1 attribution 모델

v1: **last-touch within window**. 사용자가 캠페인 메시지를 받은 후 정해진 윈도우 안에 행동(OPEN/CLICK/CONVERT)하면 **그 시각으로부터 가장 가까운 직전 발송**이 기여를 받는다.

v2 이후: multi-touch (linear, time-decay 등)는 ADR로 별도 결정.

### 4.2 attribution window

| 행동 | 윈도우 | 정당성 |
|---|---|---|
| **OPEN** | 발송 후 **7일** | 푸시 알림은 며칠 뒤에도 잠금화면에서 발견될 수 있다. 7일이 통상 기준. |
| **CLICK** | 발송 후 **7일** | OPEN과 동일 윈도우. 보통 OPEN 이후 발생하므로 OPEN의 윈도우를 따라간다. |
| **CONVERT (구매·가입)** | 발송 후 **30일** | 구매 의사결정은 며칠 걸릴 수 있어 OPEN/CLICK보다 길게 잡는다. 업계 표준 30일. |
| **DISMISS** | 발송 후 **24시간** | 단순 무시 행동은 즉각적이라 단기 윈도우만 의미. KPI 분자 아님. |

윈도우는 **발송 시각 기준**이고 사용자 액션이 그 안에 들어와야 attribution 후보가 된다.

### 4.3 last-touch 결정 절차

같은 사용자가 윈도우 안에서 여러 캠페인을 받은 경우:

1. 액션 시각(`action_at`)을 기준으로
2. 그보다 이른 발송(`notification_logs.sent_at`) 중
3. 본인(`user_id` 일치)에게 가는 것 중
4. 액션 종류에 맞는 윈도우 안에 있는 것 중
5. **가장 최근(`sent_at`이 가장 큰)** row가 attribution 대상.

해당 발송이 둘 이상 있어 동시각이라면 `log_id`가 큰 쪽(나중에 INSERT된 쪽)을 선택한다 (deterministic tie-break).

### 4.4 channel 영향

같은 사용자가 PUSH와 EMAIL을 모두 받은 경우 channel 구분 없이 last-touch만 본다 — 사용자 입장에서는 "어떤 캠페인이 마지막으로 와닿았는가"가 의미 있다. channel별 효과 비교는 별도 지표(`campaign_stats_*` channel 차원)에서 다룬다.

### 4.5 익명·로그인 머지

발송 시점에는 `user_id`가 있지만 액션 시점에는 익명 device로만 들어올 수 있다 — 푸시 토큰 만료, 비로그인 클릭 등. v1은 다음 정책:

- `user_action_logs.user_id`가 있으면 그것을 기준으로 attribution 매칭
- `user_id IS NULL`이면 attribution 매칭에서 제외하고 raw 카운트만 잡는다 (별도 표시 없음)
- 익명 ↔ 로그인 사용자 ID 머지(`S14P31B101-97`)가 적용되면 그 이후 액션은 매칭됨

**소급 적용 안 함** — 머지 시점 이후 액션만 영향. 과거 익명 액션을 머지로 재분류하면 집계가 흔들리므로 운영 정책 차원에서 거부.

---

## 5. 지연 이벤트 처리

### 5.1 지연(late-arriving) 이벤트 정의

집계 배치가 이미 돌아간 버킷에 속하는 이벤트가 나중에 도착하는 경우. 예: SDK 오프라인 → 다음 날 온라인 시 일괄 발송. 액션 시각은 어제지만 INSERT는 오늘.

### 5.2 v1 정책

- **action_at(이벤트 발생 시각)을 기준**으로 집계한다 (created_at 아님). 지연 도착해도 원래 시각의 버킷으로 계산.
- 매 daily 배치는 **직전 7일**(D-1 ~ D-7) 버킷을 재집계한다 (ADR-0007 §"daily 롤업").
- 7일 이후 도착한 이벤트는 카운트되지 않는다 — 운영 로그 + DLQ로 모니터링하고, 다량 발생 시 별도 운영 도구로 backfill.

### 5.3 attribution window vs 재집계 윈도우

attribution 윈도우(최대 30일, CONVERT)와 재집계 윈도우(7일)가 다르다는 점에 주의. 30일 attribution을 받는 CONVERT가 8일 늦게 도착하면 카운트는 보강되지 않는다. 운영상 허용 가능한 손실로 본다 (정밀 backfill이 필요하면 후속 티켓에서 도구화).

### 5.4 hourly 스트림 정책

5분 주기 hourly 워커는 **직전 1시간**만 처리한다 — 지연 이벤트는 daily 재집계에서 흡수.

hourly 응답이 자정 직후 잠시 비어 있을 수 있다 (당일 누적이 아직 안 쌓임) — 대시보드는 "당일 누적 = hourly + 어제 daily" 같은 합성을 하지 않고, 시간대별 추이 화면 전용으로 사용한다.

---

## 6. 운영 표준

### 6.1 시간대

모든 `bucket_*` 컬럼과 attribution 계산은 **KST(`Asia/Seoul`)** 기준이다. DB는 UTC로 저장하더라도 application 레이어에서 KST로 변환해 버킷팅한다.

UTC 기준이 필요한 외부 연동(SDK 이벤트 등)은 진입 시점에 KST로 변환 후 적재한다.

### 6.2 응답 정밀도

- 카운트: 정수
- 비율: 소수점 둘째 자리까지 (예: `93.47`). 반올림 (HALF_UP).
- 비율 분모가 0: `null`
- 매출: 정수(원). 부동소수점 사용 금지.

### 6.3 신규 KPI 추가 절차

1. ADR에 결정 근거 추가
2. 본 문서(`kpi-and-attribution.md`) 갱신
3. 집계 테이블 컬럼 추가 마이그레이션
4. 카운트/비율 코드 + 단위 테스트
5. API 응답 스키마 갱신

순서를 어기면 정의와 구현 간 drift가 발생한다.

---

## 7. 변경 이력

| 일자 | 내용 | 출처 |
|---|---|---|
| 2026-05-04 | 최초 작성 (S14P31B101-76) — ADR-0007의 카운트/비율을 정착, attribution 기준 신설 | 본 PR |
