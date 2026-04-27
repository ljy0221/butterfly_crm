# 세그먼트 조건 트리 스펙 개정 및 이벤트 집계 테이블 협의 제안서

<!-- markdownlint-disable MD022 MD032 MD029 MD024 MD031 MD040 -->

> **⚠️ Outdated (역사 자료)** — 본 제안서는 2026-04-27 요구명세서 개정 후 [ADR-0006](../adr/0006-segment-condition-spec-v2.md)으로 결정되었다.
> 일부 제안(`condition_json` 컬럼명, STATIC/DYNAMIC 하이브리드, `event_daily_counts`)은 채택되지 않았고,
> 일부(JSON Discriminated Union, EVENT 확장, `schemaVersion`)는 ADR-0006에 반영되었다.
> 새 구현은 ADR-0006을 따른다. 본 문서는 결정 맥락 보존용으로 유지한다.

**작성일:** 2026-04-22
**작성자:** 역할 3 (세그먼트 엔진)
**대상:** 역할 3 (세그먼트 엔진), 역할 6 (분석/통합 검증), 역할 2 (이벤트 수집 담당)
**상태:** outdated — ADR-0006으로 결정됨
**관련 ADR:** [ADR-0001](../adr/0001-segment-condition-tree-json.md) (superseded), [ADR-0006](../adr/0006-segment-condition-spec-v2.md) (본 제안서 일부 흡수, 일부 폐기)

---

## 0. 요약 (TL;DR)

[ADR-0001](../adr/0001-segment-condition-tree-json.md)은 세그먼트 조건 트리를 JSON 컬럼으로 저장하는 큰 방향은 확정했지만, **실제 세그먼트 엔진 구현 요건**(EVENT 집계, 성능 가드레일, STATIC/DYNAMIC 하이브리드)을 반영하지 못한 초기 스펙이다. 03-segment.md 구현 문서를 쓰며 다음 6개 영역을 수정 제안한다.

**스펙 개정(Blocker — 파서·빌더·validator·DB 컬럼명 전부에 영향):**
1. JSON 스키마 컬럼명 변경 (`condition_tree` → `condition_json`)
2. 그룹/리프 노드 표기 변경 (`op`/`children` → `operator`+`type`/`conditions`)
3. 리프 필드 구조 타입별 분리 (EVENT는 `eventName`/`aggregation`/`timeWindowDays` 추가)
4. 트리 깊이 제한 10 → 3 (성능 가드레일)
5. `rule_type` → `type` 네이밍
6. `schemaVersion` 필드 도입 (마이그레이션 대비)

**운영 합의 필요(이미 03-segment.md에 포함, 팀 확정 필요):**
7. STATIC/DYNAMIC 하이브리드 + 델타 스냅샷 + 커서 스트리밍 확정
8. DYNAMIC 복잡도 가드레일 (EVENT 3개, timeWindowDays 30일, 노드 10개)

**이벤트 수집 담당 협의(Blocker):**
9. `event_daily_counts` 집계 테이블 신설 — 소유 주체는 이벤트 수집 담당, 세그먼트 엔진은 읽기만

**이미 팀 결정과 일치해 재확인만 필요:**
10. `segment_rules` 테이블 삭제 (조회 최적화 격하 대신 완전 제거)
11. IN 연산자 `value` 배열 포맷 (쉼표 문자열 → 배열)

---

## 1. 배경

### 1-1. ADR-0001의 한계

[ADR-0001](../adr/0001-segment-condition-tree-json.md)은 2026-04-21에 역할 3 주도로 `accepted` 확정되었다. 당시 결정의 핵심은:

- 조건 트리를 관계형 자기참조 테이블(Option A) 대신 JSON 컬럼(Option B)에 저장
- UI 빌더 상태 ↔ JSON 1:1 매핑
- 스키마 확장성 (rule_type 추가 시 마이그레이션 불필요)

그러나 **JSON 트리의 실제 노드 구조**는 아래 3줄만 정의돼 있다:

> - `op` ∈ `{AND, OR, LEAF}` — LEAF는 `children` 없이 `ruleType/field/operator/value` 보유
> - 내부 노드는 `children`을 1개 이상 보유
> - 최대 트리 깊이 제한은 서비스 계층에서 둔다 (초안: 10)

이 구조로는 requirements 3-3의 "**최근 N일 이벤트 M회 이상**" 같은 EVENT 집계 조건을 표현할 수 없다. 리프가 `field/operator/value` 4필드뿐이라 `aggregation`(COUNT/EXISTS/NOT_EXISTS)과 `time_window_days` 칸이 없다.

### 1-2. 구현 문서 작성 중 식별된 추가 요건

03-segment.md 구현 문서를 쓰는 과정에서 다음이 추가 식별되었다:

1. **성능 가드레일** — 유저 100만 규모에서 단일 DYNAMIC 세그먼트가 DB를 멈추는 것을 막으려면 `condition_json` 저장 단계에서 복잡도 상한을 강제해야 함
2. **STATIC 스냅샷** — 델타 업데이트(전체 재계산 금지), `segment_members` 테이블
3. **`/resolve` 커서 스트리밍** — 발송 워커가 100MB 페이로드를 한 번에 받으면 OOM
4. **`event_daily_counts` 집계 테이블** — DYNAMIC EVENT 조건 평가를 3~10초에서 수십 ms로 단축하는 핵심. 이벤트 수집 파이프라인과 연결되므로 역할 3 단독 결정 불가

이 중 1~3은 세그먼트 엔진 내부 결정이지만, 4는 **이벤트 수집 담당과의 협의가 필수**다.

### 1-3. `segment_rules` 처리

ADR-0001은 "`segment_rules`는 조회 최적화용 평탄 인덱스로 격하"한다고 기술했으나, 후속 팀 논의에서 **완전 삭제로 합의되었다**(조건 트리의 단일 저장소는 `segments.condition_json`으로 일원화). 본 제안서도 삭제를 전제로 작성되며, erd.md에서는 이미 제거됐다.

---

## 2. 스펙 개정안 (ADR-0001 대비 차이점)

### 2-1. 컬럼명: `condition_tree` → `condition_json`

| 구분 | ADR-0001 | 개정안 |
|---|---|---|
| 컬럼명 | `condition_tree` | `condition_json` |
| 타입 | `JSON NOT NULL` | `JSON NOT NULL` |

**변경 이유:**
- `_tree`는 자료구조 내부 명칭이라 DB 관점에서는 `_json` 접미사가 일관됨 (`campaigns.data JSON`, `audit_logs.metadata JSON` 등 기존 컨벤션)
- 자료구조(트리)가 아닌 저장 형식(JSON)으로 드러내는 편이 직관적

**파급 효과:** ERD, 엔티티 매핑, 네이티브 쿼리 참조 모두 교체 필요. 현 시점 구현 전이라 비용 낮음.

---

### 2-2. 그룹/리프 노드 표기 전면 교체

**ADR-0001 방식:**
```json
{
  "op": "AND",
  "children": [
    { "op": "LEAF", "ruleType": "TAG", "field": "region", "operator": "EQ", "value": "서울" },
    {
      "op": "OR",
      "children": [ ... ]
    }
  ]
}
```

- 키 `op` 하나로 `AND`/`OR`/`LEAF` 3종 분기
- 자식 배열 키: `children`

**개정안:**
```json
{
  "schemaVersion": 1,
  "operator": "AND",
  "conditions": [
    { "type": "TAG", "field": "region", "operator": "EQ", "value": "서울" },
    {
      "operator": "OR",
      "conditions": [ ... ]
    }
  ]
}
```

- 그룹 노드는 `operator` 키, 리프는 `type` 키로 **구조적으로** 분기
- 자식 배열 키: `conditions`
- 루트에 `schemaVersion` 필수

**변경 이유:**
- ADR-0001 방식은 `op: "LEAF"`라는 메타 값을 써서 트리 구조와 조건 의미를 섞는데, 파서가 매번 "`op`가 LEAF면 이 노드는 리프로 취급"이라는 타입 캐스팅을 해야 함
- 개정안은 **노드에 `operator`가 있으면 그룹, `type`이 있으면 리프**로 JSON 구조만 보고 판별 가능 (Discriminated Union)
- 프런트 빌더도 TypeScript 유니온 타입으로 1:1 매핑이 쉬움:
  ```ts
  type ConditionNode = GroupNode | LeafNode;
  type GroupNode = { operator: 'AND' | 'OR'; conditions: ConditionNode[] };
  type LeafNode = TagNode | EventNode | DeviceNode | UserNode;
  ```

**파급 효과:** JSON → SQL 변환 엔진 로직 교체, UI 빌더 상태 직렬화 포맷 교체, validator 교체. 구현 전이라 현 시점 비용 낮음.

---

### 2-3. 리프 필드 구조를 타입별로 분리

**ADR-0001 방식 (평탄 구조, 단일):**
```json
{ "op": "LEAF", "ruleType": "EVENT", "field": "purchase_complete", "operator": "GTE", "value": "3" }
```

→ 모든 리프가 `field`/`operator`/`value` 4필드로 고정. **`aggregation`·`time_window_days` 저장 불가**.

**개정안 (타입별 필드 셋 차등):**

TAG / DEVICE / USER는 4필드 유지:
```json
{ "type": "TAG", "field": "region", "operator": "EQ", "value": "서울" }
{ "type": "DEVICE", "field": "platform", "operator": "EQ", "value": "ANDROID" }
{ "type": "USER", "field": "pushOptIn", "operator": "EQ", "value": true }
```

EVENT는 집계·시간창 필드 추가:
```json
{
  "type": "EVENT",
  "eventName": "purchase",
  "aggregation": "COUNT",
  "operator": "GTE",
  "value": 3,
  "timeWindowDays": 7
}
```

`aggregation`별 필요 필드:
| aggregation | operator | value | 예시 |
|---|---|---|---|
| `EXISTS` | — | — | 1회 이상 발생한 유저 |
| `NOT_EXISTS` | — | — | 미발생 유저 (휴면) |
| `COUNT` | 필수 | 필수 | 발생 횟수 비교 |

**변경 이유:**
- requirements 3-3은 "최근 N일 이벤트 M회 이상" 조건을 필수로 요구 — ADR-0001 구조로는 저장 불가
- EVENT는 본질적으로 "이벤트명 × 집계함수 × 시간창 × 비교"라 4필드로 강제하면 `field`를 다중 용도로 오버로드해야 함 (예: ADR-0001 예시는 `field: "purchase_complete"` ← 이건 eventName인데 TAG.field와 이름 충돌)
- `value` 타입도 TAG는 string/array, EVENT.COUNT는 number, USER.pushOptIn은 boolean 등 타입별 차이가 자연스럽게 반영됨

**파급 효과:** JSON Schema validator가 `type`별로 분기되는 6-oneOf 구조로 커짐. 대신 JSON → SQL 변환이 타입별 전용 빌더를 써서 단순해짐.

---

### 2-4. 트리 깊이 제한 10 → 3

| 항목 | ADR-0001 | 개정안 |
|---|---|---|
| 최대 깊이 | 10 (초안) | 3 |

**변경 이유:**
- ADR-0001은 "서비스 계층 제약"으로 느슨하게 초안만 제시 (10)
- 깊이 3 + EVENT 조건 3개 + 전체 노드 10개 + `timeWindowDays` 30일의 **4가지 가드레일 세트**로 DYNAMIC 쿼리 복잡도 상한을 정한다
- 유저 100만 × 이벤트 수천만 row 환경에서 깊이 10 + EVENT 중첩이 들어가면 단일 DYNAMIC 세그먼트 하나가 전체 DB 커넥션을 점유 → 서비스 마비 가능
- 깊이 3은 현업 세그먼트 요구사항 ("(A AND B) OR (C AND D)")을 커버하면서도 쿼리 복잡도 상한을 예측 가능하게 만듦
- 초과 시 `SEG_009` 에러 + STATIC 전환 권고 메시지

**파급 효과:** validator에 깊이 카운트 로직 1개 추가. 사용자에게는 UI 빌더에서 4단 중첩을 시도하면 경고가 뜨는 형태로 노출.

---

### 2-5. `ruleType` → `type`

| 구분 | ADR-0001 | 개정안 |
|---|---|---|
| 리프 타입 키 | `ruleType` | `type` |

**변경 이유:**
- `segment_rules` 테이블 삭제 결정에 따라 "`rule`"이라는 잔재 용어를 정리
- JSON 스키마 내부에서만 의미 있는 값이므로 간결한 `type`이 자연스러움

**파급 효과:** 순수 네이밍. 영향 받는 곳은 JSON Schema + 파서 + UI 상수.

---

### 2-6. `schemaVersion` 필드 도입

**ADR-0001:** 스키마 버전 필드 없음 (`Consequences`에 "JSON 스키마 변경 시 기존 데이터 마이그레이션 규칙을 ADR로 남겨야 한다"고만 기술)

**개정안:** 루트 노드 필수 필드로 `schemaVersion: 1` 추가

```json
{
  "schemaVersion": 1,
  "operator": "AND",
  "conditions": [ ... ]
}
```

**변경 이유:**
- MVP 이후 조건 타입이 추가되거나 EVENT `SUM`/`eventDataFilter`가 들어갈 때 breaking change 마이그레이션이 불가피
- 루트에 버전 필드가 있어야 파서가 "이 레코드는 v1이니 현재 파서로, v2면 새 파서로"를 조건 분기할 수 있음
- 버전 필드 없이 시작하면 나중에 마이그레이션할 때 모든 기존 레코드를 역추적해야 함

**파급 효과:** validator에 1줄 추가. 저장 크기 영향 없음 (JSON 10바이트 증가).

---

## 3. 운영 합의 필요 항목 (03-segment.md 반영분)

ADR-0001에는 없으나 03-segment.md 구현 문서에 기술된 내용. ADR-0006(supersede) 또는 후속 ADR로 승격 필요.

### 3-1. STATIC/DYNAMIC 하이브리드

| 값 | 설명 |
|---|---|
| DYNAMIC | 발송 시점에 조건을 즉석 쿼리로 평가 (실시간성 ↑, 복잡도 상한 있음) |
| STATIC | 사전 스냅샷(`segment_members`) 기반 평가 (대규모/복잡 조건에 적합) |

`segments.evaluation_type ENUM('DYNAMIC','STATIC') NOT NULL DEFAULT 'DYNAMIC'`

### 3-2. STATIC 스냅샷 — 델타 업데이트 필수

단순 전체 재계산(DELETE + INSERT 10만 row) 금지. 이유:
- 전체 재계산 (10만 row): 5~15초
- 델타 업데이트 (변경 5%, 5천 row): 0.3~1초

구현 요구:
1. 조건 쿼리로 현재 매칭 userId 집합 계산 → 메모리 Set
2. 기존 `segment_members` 조회 → 이전 집합 로드
3. diff: (현재 − 이전) INSERT, (이전 − 현재) DELETE
4. 변경분만 트랜잭션 반영

### 3-3. `/resolve` — 커서 기반 청크 스트리밍 필수

전체 결과 한 번에 반환 금지. 10만 유저 × 디바이스 포함 시 페이로드 80MB → OOM 위험.

1. 커서 기반 페이지네이션 (OFFSET 금지)
2. 청크 사이즈 기본 1000건
3. JSON Lines 스트리밍(권장) 또는 커서 페이징 응답
4. 첫 청크 5초 타임아웃 초과 시 `SEG_011` + STATIC 전환 권고

### 3-4. DYNAMIC 복잡도 가드레일

저장 시 `condition_json` 검증 단계에서 아래 제약 강제. 초과 시 `SEG_009`.

| 제약 | 값 | 이유 |
|---|---|---|
| EVENT 조건 개수 | 최대 3개 | GROUP BY 중첩 방지 |
| `timeWindowDays` | 최대 30일 | 집계 테이블 범위 제한 |
| 중첩 깊이 | 최대 3단 | 파서 복잡도/성능 |
| 전체 Condition 노드 수 | 최대 10개 | 쿼리 복잡도 상한 |

### 3-5. 캐싱 전략

- **HLL 결과**: Redis, key=`seg:hll:{segmentId}`, TTL 1시간
- **resolve 결과**: `campaign:{id}:resolvedUsers` 키로 Redis 적재, 발송 완료 후 폐기
- **STATIC 스냅샷**: `segment_members` 테이블 자체가 영구 캐시

---

## 4. 이벤트 수집 담당 협의 — `event_daily_counts` 테이블

> 이 절은 기존 `세그먼트_이벤트수집_협의요청.md`를 본 제안서로 통합한 것이다. 원 문서는 삭제.

### 4-1. 요청 요약

DYNAMIC 세그먼트의 EVENT 조건 평가를 실무 수준 성능으로 돌리기 위해, `events` 원본과 별도로 **유저·이벤트·일자 단위로 사전 집계된 테이블**이 필요하다. 이 테이블의 **스키마 정의, DDL 반영, 데이터 적재, 운영**은 이벤트 수집 모듈 범위이므로 생성·유지보수를 요청한다.

세그먼트 엔진은 **읽기 전용**으로 소비하며, 쓰기(INSERT/UPDATE/DELETE)는 하지 않는다.

### 4-2. 왜 필요한가

세그먼트 `conditionJson`의 EVENT 조건(예: "최근 7일 `add_to_cart` 3회 이상")을 `events` 원본에 직접 집계 쿼리로 평가하면:

- 유저 100만 + 이벤트 누적 수천만 row 기준, `GROUP BY user_id` 포함 쿼리 1건당 **3~10초 이상**
- 세그먼트 `preview`(5초 타임아웃)와 `resolve`(발송 대상자 확정) 양쪽 모두 사실상 사용 불가
- 단일 DYNAMIC 세그먼트 하나가 DB를 점유해 전체 서비스에 영향

`event_daily_counts`처럼 "유저 × 이벤트 × 일자"로 사전 집계된 테이블이 있으면 동일 조건 평가가 **수십~수백 ms** 수준으로 떨어진다.

### 4-3. 요청 스키마 (초안)

```sql
CREATE TABLE `event_daily_counts` (
    `project_id` BIGINT       NOT NULL COMMENT '소속 프로젝트 ID (tenant)',
    `user_id`    BIGINT       NOT NULL COMMENT '유저 ID',
    `event_name` VARCHAR(255) NOT NULL COMMENT '이벤트명 (events.event_name과 동일)',
    `event_date` DATE         NOT NULL COMMENT '이벤트 발생 일자 (Asia/Seoul 기준)',
    `count`      INT          NOT NULL COMMENT '해당 일자 이벤트 발생 횟수',
    PRIMARY KEY (`project_id`, `event_name`, `event_date`, `user_id`)
);
```

**PK 순서 설계 이유:**
- 세그먼트 평가는 `(project_id, event_name, event_date 범위)` 필터 후 `user_id` 집합을 얻는 패턴
- PK 선두에 `project_id, event_name, event_date`를 배치하여 범위 스캔이 커버링 인덱스로 수행
- `user_id`는 마지막에 두어 중복 없는 unique row 보장

**타임존:** 서버 JVM이 `Asia/Seoul` 고정(ADR-0003과 동일)이므로 `event_date`도 같은 기준.

### 4-4. 적재 방식 (선택지)

세그먼트 엔진은 "결과적 일관성"만 보장되면 되므로 이벤트 수집 담당이 편한 쪽으로 선택:

| 옵션 | 설명 | 장점 | 단점 |
|---|---|---|---|
| A. 스트리밍 UPSERT | 이벤트 수집 시 `ON DUPLICATE KEY UPDATE count = count + 1` | 준실시간 | 쓰기 부하 2배 |
| B. 새벽 배치 | 매일 KST 03:00 전일자 `events` → 집계 INSERT | 쓰기 부하 낮음 | 최대 24시간+ 지연 |
| C. 하이브리드 | 당일자만 스트리밍, 과거일자는 배치 보정 | 실시간성 + 정합성 | 구현 복잡도 ↑ |

세그먼트 쪽 최소 요구사항:
- **지연 허용 한계**: 24시간 이내 (STATIC 스냅샷 재계산 주기와 맞춤)
- **동일 일자 동일 유저 중복 row 금지** (PK로 보장)
- **`project_id` 단위 멀티테넌시 격리** 유지

### 4-5. 세그먼트 엔진이 거는 쿼리 패턴 (읽기만)

예시 1 — "최근 7일 `add_to_cart` 3회 이상"
```sql
SELECT user_id
FROM event_daily_counts
WHERE project_id = :projectId
  AND event_name = 'add_to_cart'
  AND event_date >= CURRENT_DATE - INTERVAL 7 DAY
GROUP BY user_id
HAVING SUM(count) >= 3;
```

예시 2 — "최근 30일 `purchase` EXISTS"
```sql
SELECT DISTINCT user_id
FROM event_daily_counts
WHERE project_id = :projectId
  AND event_name = 'purchase'
  AND event_date >= CURRENT_DATE - INTERVAL 30 DAY;
```

예시 3 — "최근 30일 `purchase` NOT_EXISTS" → 위 결과의 여집합 (`users` LEFT JOIN)

### 4-6. 보관 기간

EVENT 조건의 `timeWindowDays` 최대 30일(§2-4, §3-4 가드레일)이므로 **최대 30일치만 쿼리 대상**. 보관 기간은 **30~90일** 선에서 이벤트 수집 담당이 결정. 후속 확장(리텐션 분석) 요구 시 재논의.

### 4-7. 영향 범위

**테이블이 마련되지 않으면:**
- DYNAMIC 세그먼트의 EVENT 조건을 MVP에서 제공 불가 → TAG/DEVICE/USER 조건만 가능
- EVENT 조건이 필요한 세그먼트는 STATIC 전용으로만 운영 (실시간성 포기)
- 캠페인 발송 `/resolve` API의 첫 청크 타임아웃 초과(`SEG_011`) 빈발 예상

**테이블이 마련되면:**
- EVENT 조건을 포함한 DYNAMIC 세그먼트 preview/resolve가 모두 1초 이내로 동작
- "최근 N일 ○○한 유저"류 세그먼트가 MVP에 포함 가능

---

## 5. 파급 효과 요약

| 영역 | 영향 |
|---|---|
| ERD (`segments.condition_json`) | 컬럼명 `condition_tree` → `condition_json` (아직 구현 전, 비용 낮음) |
| 백엔드 JSON 파서 | Discriminated union 기반 파서로 전면 교체. 6가지 오브젝트 타입(Group + TAG/EVENT/DEVICE/USER)에 각각 매퍼 |
| 백엔드 JSON → SQL 변환기 | 타입별 전용 빌더 (특히 EVENT는 `event_daily_counts` 참조) |
| 백엔드 validator | 깊이/노드수/EVENT 개수/`timeWindowDays` 가드레일 |
| 프런트 빌더 UI | 조건 노드 컴포넌트 타입별 분리, TypeScript 유니온 타입 정의 |
| 이벤트 수집 모듈 | `event_daily_counts` 테이블 신설·적재·운영 (협의 필요) |
| 분석 모듈 (역할 6) | `segment_metric_daily`와는 직접 관계 없음. 세그먼트 평가 결과 소비 방식만 확인 |

---

## 6. 합의 필요 체크리스트

### 6-1. 역할 3 (세그먼트 엔진) — 내부 확정 사항

- [ ] 2-1 컬럼명 `condition_json` 채택
- [ ] 2-2 노드 표기 (`operator`+`type`/`conditions`) 채택
- [ ] 2-3 리프 필드 타입별 분리 (EVENT 확장 필드 포함) 채택
- [ ] 2-4 트리 깊이 3 채택
- [ ] 2-5 `ruleType` → `type` 채택
- [ ] 2-6 `schemaVersion` 필수 채택
- [ ] 3-1 ~ 3-5 STATIC/DYNAMIC 하이브리드 + 가드레일 채택

### 6-2. 역할 6 (분석/통합 검증) — 교차 검증

- [ ] §2 스키마 변경이 세그먼트 성과 분석(`segment_metric_daily`) 파이프라인에 영향 없음을 확인
- [ ] `segment_id` 기반 집계 외에 `condition_json` 내부를 역직렬화할 일이 없음을 확인
- [ ] `/resolve` 스트리밍 응답 포맷이 발송 모듈과 호환되는지 확인

### 6-3. 이벤트 수집 담당 — `event_daily_counts`

- [ ] 4-1 테이블 이름 `event_daily_counts` 수용 (다른 네이밍 제안 가능)
- [ ] 4-3 스키마 컬럼·타입·PK 초안 수용 여부
- [ ] 4-4 적재 방식 선택 (A/B/C 중, 또는 제3안)
- [ ] 4-3 `event_date` 기준 타임존 확인 (Asia/Seoul)
- [ ] 4-6 데이터 보관 기간 (30/60/90일 중)
- [ ] 백필 계획 — 기존 `events`에서 초기 집계 1회 필요 여부
- [ ] `erd.md` 반영 PR 담당 (이벤트 수집 담당)
- [ ] SLA — 스트리밍 선택 시 허용 지연, 배치 선택 시 실행 시각

### 6-4. 팀 재확인 (이미 결정된 것으로 보이는 항목)

- [ ] `segment_rules` 테이블 완전 삭제 유지 (ADR-0001의 "격하" 표현 무효화)
- [ ] IN 연산자 `value` 배열 포맷 (`["VIP","VVIP"]`) 유지

---

## 7. 승인 시 후속 작업

1. **ADR-0007 (가칭) 신규 작성** — 본 제안서 내용 확정 + `status: accepted`
   - `status: superseded by ADR-0007` 로 [ADR-0001](../adr/0001-segment-condition-tree-json.md) 상태 변경
   - 이벤트 수집 담당 합의 결과는 ADR 별도 섹션 또는 ADR-0008로 분리 (이벤트 수집 담당 책임 영역이므로)
2. **erd.md 최종화**
   - `segments.condition_json`(본 제안 컬럼명) 확정
   - `event_daily_counts`는 이벤트 수집 담당이 추가
3. **03-segment.md 경고 제거** — 상단의 "proposals/03에서 재협의 중" 배너 제거
4. **프런트 세그먼트 빌더 타입 정의 배포**
5. **세그먼트 엔진 구현 착수** — JSON Schema validator, JSON → SQL 변환기, `/resolve` 스트리밍

---

## 8. 참고

- 선행 ADR: [ADR-0001 세그먼트 조건 트리를 JSON 컬럼으로 저장](../adr/0001-segment-condition-tree-json.md)
- 구현 문서: [api/03-segment.md](../api/03-segment.md)
- 관련 ADR: [ADR-0003 분석용 일자별 집계 테이블 도입](../adr/0003-analytics-aggregation-tables.md) (타임존 정책)
- ERD: [erd.md](../erd.md)
- 요구사항: [requirements.md](../requirements.md) 3-3 (AND/OR 중첩), 3-6 (집계 테이블 우선 조회)

---

## 9. 변경 이력

- 2026-04-22: 초안 작성. `세그먼트_이벤트수집_협의요청.md`를 §4로 흡수 후 원문서 삭제.
