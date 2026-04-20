# Auth 모듈 API (역할1: 인증/프로젝트/권한)

담당 테이블: `members`, `projects`, `project_members`

---

## 1. 회원 인증

| # | Method | URL | 설명 | 인증 | 비고 |
|---|--------|-----|------|------|------|
| 1 | POST | `/api/auth/signup` | 회원가입 | 없음 | |
| 2 | POST | `/api/auth/login` | 로그인 | 없음 | JWT 발급 |
| 3 | POST | `/api/auth/logout` | 로그아웃 | JWT | Refresh Token 무효화 |
| 4 | POST | `/api/auth/refresh` | 토큰 갱신 | Refresh Token | Access Token 재발급 |
| 5 | GET | `/api/auth/me` | 내 정보 조회 | JWT | |

### POST /api/auth/signup

```json
// Request
{
  "email": "user@example.com",
  "password": "password123!",
  "name": "홍길동"
}

// Response
{
  "memberId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "createdAt": "2026-04-20T10:00:00"
}
```

### POST /api/auth/login

```json
// Request
{
  "email": "user@example.com",
  "password": "password123!"
}

// Response
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "memberId": 1,
  "name": "홍길동"
}
```

---

## 2. 프로젝트 관리

| # | Method | URL | 설명 | 최소 권한 | 비고 |
|---|--------|-----|------|----------|------|
| 6 | POST | `/api/projects` | 프로젝트 생성 | JWT | 생성자가 OWNER |
| 7 | GET | `/api/projects` | 내 프로젝트 목록 | JWT | 내가 속한 프로젝트만 |
| 8 | GET | `/api/projects/{projectId}` | 프로젝트 상세 | VIEWER | |
| 9 | PUT | `/api/projects/{projectId}` | 프로젝트 수정 | ADMIN | name, limit 등 |
| 10 | DELETE | `/api/projects/{projectId}` | 프로젝트 삭제 | OWNER | |
| 11 | POST | `/api/projects/{projectId}/api-keys/regenerate` | API 키 재발급 | OWNER | 감사 로그 기록 |
| 12 | PUT | `/api/projects/{projectId}/fcm-credentials` | FCM 자격증명 등록/수정 | ADMIN | AES 암호화 저장, 감사 로그 |
| 13 | GET | `/api/projects/{projectId}/fcm-credentials` | FCM 자격증명 조회 | ADMIN | 마스킹 표시, 감사 로그 |

### POST /api/projects

```json
// Request
{
  "name": "쇼핑몰 앱"
}

// Response
{
  "projectId": 1,
  "name": "쇼핑몰 앱",
  "publicApiKey": "pk_live_abc123",
  "secretApiKey": "sk_live_xyz789",
  "createdAt": "2026-04-20T10:00:00"
}
```

---

## 3. 프로젝트 멤버 관리

| # | Method | URL | 설명 | 최소 권한 | 비고 |
|---|--------|-----|------|----------|------|
| 14 | POST | `/api/projects/{projectId}/members` | 멤버 초대 | ADMIN | 이메일로 초대 |
| 15 | GET | `/api/projects/{projectId}/members` | 멤버 목록 | VIEWER | |
| 16 | PUT | `/api/projects/{projectId}/members/{memberId}/role` | 역할 변경 | OWNER | 감사 로그 기록 |
| 17 | DELETE | `/api/projects/{projectId}/members/{memberId}` | 멤버 제거 | ADMIN | |

### POST /api/projects/{projectId}/members

```json
// Request
{
  "email": "newmember@example.com",
  "role": "EDITOR"
}
```

### PUT /api/projects/{projectId}/members/{memberId}/role

```json
// Request
{
  "role": "ADMIN"
}
```
