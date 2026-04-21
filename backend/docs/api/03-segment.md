# Segment 모듈 API (역할3: 세그먼트 엔진)

담당 테이블: `segments`, `segment_rules`

---

## 세그먼트 관리

| # | Method | URL | 설명 | 최소 권한 | 비고 |
|---|--------|-----|------|----------|------|
| 37 | POST | `/api/projects/{projectId}/segments` | 세그먼트 생성 | EDITOR | 규칙(rules)과 함께 생성 |
| 38 | GET | `/api/projects/{projectId}/segments` | 세그먼트 목록 | VIEWER | |
| 39 | GET | `/api/projects/{projectId}/segments/{segmentId}` | 세그먼트 상세 | VIEWER | 규칙 포함 |
| 40 | PUT | `/api/projects/{projectId}/segments/{segmentId}` | 세그먼트 수정 | EDITOR | 규칙 전체 교체 |
| 41 | DELETE | `/api/projects/{projectId}/segments/{segmentId}` | 세그먼트 삭제 | EDITOR | |
| 42 | POST | `/api/projects/{projectId}/segments/preview` | 미리보기 (예상 모수) | EDITOR | 저장 전 조건으로 계산 |
| 43 | GET | `/api/projects/{projectId}/segments/{segmentId}/users` | 대상 유저 목록 | VIEWER | 페이지네이션 |

### POST /api/projects/{projectId}/segments

```json
// Request
{
  "name": "서울 VIP 유저",
  "rules": [
    {
      "ruleType": "TAG",
      "field": "region",
      "operator": "EQ",
      "value": "서울"
    },
    {
      "ruleType": "TAG",
      "field": "grade",
      "operator": "IN",
      "value": "VIP,VVIP"
    }
  ]
}

// Response
{
  "segmentId": 1,
  "name": "서울 VIP 유저",
  "estimatedCount": 2340,
  "rules": [
    {
      "ruleId": 1,
      "ruleType": "TAG",
      "field": "region",
      "operator": "EQ",
      "value": "서울"
    },
    {
      "ruleId": 2,
      "ruleType": "TAG",
      "field": "grade",
      "operator": "IN",
      "value": "VIP,VVIP"
    }
  ],
  "createdAt": "2026-04-20T10:00:00"
}
```

### POST /api/projects/{projectId}/segments/preview

```json
// Request — 저장하지 않고 조건만 보내서 예상 모수 확인
{
  "rules": [
    {
      "ruleType": "TAG",
      "field": "region",
      "operator": "EQ",
      "value": "서울"
    }
  ]
}

// Response
{
  "estimatedCount": 5120
}
```

---

## 규칙 타입 (rule_type)

| 타입 | 설명 | 예시 |
|------|------|------|
| TAG | 유저 태그 조건 | region = 서울 |
| EVENT | 이벤트 발생 조건 | purchase_complete 발생 유저 |
| DEVICE | 디바이스 속성 조건 | platform = ANDROID |
| USER | 유저 속성 조건 | push_opt_in = true |

## 연산자 (operator)

| 연산자 | 설명 |
|--------|------|
| EQ | 같음 |
| NEQ | 같지 않음 |
| GT | 초과 |
| LT | 미만 |
| GTE | 이상 |
| LTE | 이하 |
| IN | 포함 (쉼표 구분, 예: "VIP,VVIP") |
| CONTAINS | 문자열 포함 |
