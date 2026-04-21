# API 공통 규칙

---

## 인증 방식

| 구분 | 인증 방식 | 용도 |
|------|----------|------|
| Management API | JWT (Authorization: Bearer {token}) | 관리 콘솔에서 사용하는 API |
| SDK API | API Key (X-API-Key: {public_api_key}) | 클라이언트 SDK가 호출하는 API |

---

## 공통 응답 포맷

### 성공

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

### 실패

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_001",
    "message": "인증 토큰이 만료되었습니다."
  }
}
```

---

## 페이지네이션 (목록 조회 공통)

```
GET /api/projects/{projectId}/users?page=0&size=20&sort=createdAt,desc
```

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

---

## 권한 역할

| 역할 | 설명 |
|------|------|
| OWNER | 프로젝트 소유자. 모든 권한 |
| ADMIN | 관리자. 멤버 관리, 설정 변경 가능 |
| EDITOR | 운영자. 캠페인/세그먼트 생성·수정 가능 |
| VIEWER | 분석가. 조회만 가능 |

---

## 감사 로그 대상 API

요구사항 4-6절 기준, 아래 API 호출 시 감사 로그 기록 필수:

- API 키 재발급
- FCM 자격증명 등록/수정/조회
- 멤버 역할 변경
- 캠페인 즉시 발송
- 캠페인 예약 발송
- 캠페인 발송 취소

---

## 모듈별 API 개수

| 모듈 | Management API | SDK API | 합계 |
|------|---------------|---------|------|
| Auth | 17 | - | 17 |
| Customer | 15 | 4 | 19 |
| Segment | 7 | - | 7 |
| Campaign | 8 | - | 8 |
| Delivery | 3 | 2 | 5 |
| Analytics | 5 | - | 5 |
| **합계** | **55** | **6** | **61** |

---

## 모듈별 문서

- [01-auth.md](./01-auth.md) — 인증/프로젝트/권한 (역할1, 17개 API)
- [02-customer.md](./02-customer.md) — 고객/이벤트/프로필 (역할2, 19개 API)
- [03-segment.md](./03-segment.md) — 세그먼트 엔진 (역할3, 7개 API)
- [04-campaign.md](./04-campaign.md) — 캠페인 (역할4, 8개 API)
- [05-delivery.md](./05-delivery.md) — 발송/로그 (역할5, 5개 API)
- [06-analytics.md](./06-analytics.md) — 분석/대시보드 (역할6, 5개 API)
