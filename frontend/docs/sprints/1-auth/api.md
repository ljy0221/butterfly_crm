# Sprint 1 — Auth + Application API 명세

> Backend Liaison Agent가 정리한 백엔드 → 프론트엔드 계약서.

**작성 일시**: 2026-05-01  
**백엔드 버전**: Spring Boot 3.5.13 + JWT (draft)

---

## 1. 공통 사항

### 1.1 응답 형식 (ApiResponse 래퍼)

모든 엔드포인트는 다음 구조로 응답:

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패 응답:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_001",
    "message": "이미 사용 중인 이메일입니다"
  }
}
```

---

### 1.2 인증 메커니즘

**⚠️ 백엔드 코드 직접 검증 결과 (Sprint 1 추가 확인사항)**

`AuthService.java` 라인 74 / 87에서 다음을 확인:

```java
// AuthService.login()
return new LoginResponse("", "", MemberInfoResponse.from(member));
                         //   ↑↑   ↑↑  accessToken/refreshToken 모두 빈 문자열

// AuthService.refreshToken()
throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID); // 항상 실패
```

**현재 상태**: **JWT 미구현**. 로그인은 자격증명 검증만 하고 빈 토큰을 내려준다.
- 프론트엔드 인증 동작은 **`X-Member-Id: <number>` 임시 헤더**에 의존
- 모든 보호 엔드포인트가 `@RequestHeader("X-Member-Id") Long memberId` 시그니처 사용
- accessToken/refreshToken은 빈 문자열이라도 LoginResponse 객체에 들어있어, 프론트는 `member.memberId`만 store에 저장하면 즉시 동작
- `/api/v1/auth/refresh`는 호출 시 항상 401 → 프론트의 401 인터셉터는 fail-soft하게 처리(로그아웃 유도)
- 백엔드가 JWT 통합을 마치면 frontend `client.ts` 수정 없이 자동으로 Bearer 헤더가 붙도록 이미 설계됨 (token이 빈 문자열이면 헤더 미첨부)

**JWT 설정** (application.properties):
```
jwt.access-token-expiry-ms=1800000      # 30분
jwt.refresh-token-expiry-ms=604800000   # 7일
```

---

## 2. Auth 엔드포인트

### 2.1 POST /api/v1/auth/email/send-code

**요청**: `{ email: string }`

**응답**: `200 OK`
```json
{ "success": true, "data": null, "error": null }
```

**실패**:
- `409 CONFLICT` — `AUTH_001`: 이미 가입된 이메일
- `429 TOO_MANY_REQUESTS` — `AUTH_005`: 발송 초과

---

### 2.2 POST /api/v1/auth/email/verify

**요청**:
```json
{
  "email": "user@example.com",
  "code": "a3c9f2"
}
```

**응답**: `200 OK`

**실패**:
- `400 BAD_REQUEST` — `AUTH_003`: 코드 만료 (5분)
- `400 BAD_REQUEST` — `AUTH_004`: 코드 불일치

---

### 2.3 POST /api/v1/auth/signup

**요청**:
```json
{
  "email": "user@example.com",
  "password": "Secure123",
  "name": "홍길동",
  "phone": "01012345678",
  "birthDate": "1990-05-15",
  "companyAuthNo": "1234567890",
  "agreedToTerms": true
}
```

**응답**: `201 CREATED`
```json
{
  "success": true,
  "data": {
    "memberId": 123,
    "email": "user@example.com",
    "name": "홍길동"
  },
  "error": null
}
```

**실패**:
- `400 BAD_REQUEST` — `AUTH_002`: 이메일 인증 미완료
- `400 BAD_REQUEST` — `AUTH_015`: 회사 인증번호 유효하지 않음
- `409 CONFLICT` — `AUTH_001`: 중복 이메일

---

### 2.4 POST /api/v1/auth/login

**요청**:
```json
{
  "email": "user@example.com",
  "password": "Secure123"
}
```

**응답**: `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG...",
    "member": {
      "memberId": 123,
      "email": "user@example.com",
      "name": "홍길동",
      "phone": "01012345678",
      "roleType": "VIEWER",
      "projectId": 456,
      "createdAt": "2026-05-01T10:00:00"
    }
  },
  "error": null
}
```

**현재 상태**: accessToken, refreshToken은 빈 문자열 (JWT 구현 진행 중)

**실패**:
- `401 UNAUTHORIZED` — `AUTH_006`: 이메일 또는 비밀번호 오류
- `403 FORBIDDEN` — `AUTH_016`: 비밀번호 미설정 (Owner 초대 후)

---

### 2.5 POST /api/v1/auth/logout

**인증**: 필수 (`X-Member-Id` 헤더)

**요청**: 없음

**응답**: `200 OK`

---

### 2.6 POST /api/v1/auth/refresh

**요청**:
```json
{
  "refreshToken": "eyJhbG..."
}
```

**응답**: `200 OK` (새 토큰)

**현재 상태**: 미구현 — `AUTH_012` 강제 반환

**실패**:
- `401 UNAUTHORIZED` — `AUTH_012`: 토큰 유효하지 않음
- `401 UNAUTHORIZED` — `AUTH_013`: 토큰 만료 (7일 초과)

---

### 2.7 GET /api/v1/auth/me

**인증**: 필수

**응답**: `200 OK`
```json
{
  "success": true,
  "data": {
    "memberId": 123,
    "email": "user@example.com",
    "name": "홍길동",
    "phone": "01012345678",
    "roleType": "VIEWER",
    "projectId": 456,
    "createdAt": "2026-05-01T10:00:00"
  },
  "error": null
}
```

---

### 2.8 PUT /api/v1/auth/password

**요청**:
```json
{
  "currentPassword": "old123",
  "newPassword": "new12345"
}
```

**응답**: `200 OK`

**실패**:
- `401 UNAUTHORIZED` — `AUTH_009`: 현재 비밀번호 불일치
- `400 BAD_REQUEST` — `AUTH_010`: 새 비밀번호가 현재와 동일

---

### 2.9 PUT /api/v1/auth/phone

**요청**:
```json
{
  "phone": "01087654321"
}
```

**응답**: `200 OK`

---

### 2.10 DELETE /api/v1/auth/withdraw

**요청**:
```json
{
  "password": "current123"
}
```

**응답**: `200 OK`

**실패**:
- `409 CONFLICT` — `PROJ_001`: Owner는 탈퇴 불가

---

### 2.11 POST /api/v1/auth/owner/set-password

**인증**: 불필요 (초대 토큰)

**요청**:
```json
{
  "token": "abc123def456",
  "password": "newpassword123"
}
```

**응답**: `200 OK` → 로그인 가능

**실패**:
- `400 BAD_REQUEST` — `AUTH_017`: 링크 만료 (24시간)
- `404 NOT_FOUND` — `AUTH_008`: 회원 미존재

---

## 3. Application (신청서) 엔드포인트

### 3.1 POST /api/v1/applications

**인증**: 불필요

**요청**:
```json
{
  "applicantName": "김철수",
  "email": "kim@example.com",
  "phone": "01098765432",
  "birthDate": "1990-05-15",
  "businessName": "ABC 마케팅",
  "businessNo": "1234567890"
}
```

**응답**: `200 OK`
```json
{
  "success": true,
  "data": 101,
  "error": null
}
```

**실패**:
- `409 CONFLICT` — `APPLY_002`: 중복 사업자 번호

---

### 3.2 GET /api/v1/admin/applications

**인증**: 필수 (ADMIN 역할)

**요청**: Query parameters
```
?status=PENDING&page=0&size=20&sort=createdAt,desc
```

**응답**: `200 OK` (Page 형식)
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "applicationId": 101,
        "applicantName": "김철수",
        "email": "kim@example.com",
        "phone": "01098765432",
        "birthDate": "1990-05-15",
        "businessName": "ABC 마케팅",
        "businessNo": "1234567890",
        "status": "PENDING",
        "rejectReason": null,
        "reviewedAt": null,
        "createdAt": "2026-05-01T10:30:00"
      }
    ],
    "totalElements": 42,
    "totalPages": 3,
    "currentPage": 0,
    "size": 20
  },
  "error": null
}
```

---

### 3.3 GET /api/v1/admin/applications/{applicationId}

**인증**: 필수 (ADMIN)

**응답**: `200 OK` (ApplicationResponse)

---

### 3.4 POST /api/v1/admin/applications/{applicationId}/approve

**인증**: 필수 (ADMIN)

**요청**: 없음

**응답**: `200 OK`

**백엔드 동작**:
1. status: PENDING → APPROVED
2. reviewedAt: 현재시간
3. Owner 초대 이메일 발송 (예정)

---

### 3.5 POST /api/v1/admin/applications/{applicationId}/reject

**인증**: 필수 (ADMIN)

**요청**:
```json
{
  "rejectReason": "서류 미비"
}
```

**응답**: `200 OK`

---

## 4. Role 매트릭스

| 엔드포인트 | 비로그인 | VIEWER | OWNER | ADMIN |
|---|---|---|---|---|
| POST /api/v1/auth/email/send-code | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/auth/email/verify | ✅ | ✅ | ✅ | ✅ |
| POST /api/v1/auth/signup | ✅ | ❌ | ❌ | ❌ |
| POST /api/v1/auth/login | ✅ | ❌ | ❌ | ❌ |
| GET /api/v1/auth/me | ❌ | ✅ | ✅ | ✅ |
| POST /api/v1/auth/owner/set-password | ✅ | ❌ | ❌ | ❌ |
| POST /api/v1/applications | ✅ | ✅ | ✅ | ✅ |
| GET /api/v1/admin/applications | ❌ | ❌ | ❌ | ✅ |
| POST /api/v1/admin/applications/{id}/approve | ❌ | ❌ | ❌ | ✅ |
| POST /api/v1/admin/applications/{id}/reject | ❌ | ❌ | ❌ | ✅ |

---

## 5. 핵심 플로우

### 회원가입
1. POST /api/v1/auth/email/send-code (코드 발송)
2. POST /api/v1/auth/email/verify (코드 확인)
3. POST /api/v1/auth/signup (회원가입)
4. POST /api/v1/auth/login (로그인)

### Owner 초대
1. Admin: POST /api/v1/admin/applications/{id}/approve
2. 시스템: 초대 이메일 발송 (token 포함)
3. 사용자: 링크 클릭 → token 추출
4. 사용자: POST /api/v1/auth/owner/set-password (비밀번호 설정)
5. 사용자: POST /api/v1/auth/login (로그인)

### Token 갱신
1. 토큰 만료 감지 (401)
2. POST /api/v1/auth/refresh (새 토큰 발급)
3. 원래 요청 재시도

---

## 6. 에러 코드

### Auth (AUTH_XXX)
- AUTH_001: 409 — 이미 사용 중인 이메일
- AUTH_002: 400 — 이메일 인증 필요
- AUTH_003: 400 — 인증 코드 만료
- AUTH_004: 400 — 인증 코드 불일치
- AUTH_005: 429 — 인증 코드 발송 초과
- AUTH_006: 401 — 이메일 또는 비밀번호 오류
- AUTH_008: 404 — 회원 미존재
- AUTH_009: 401 — 현재 비밀번호 불일치
- AUTH_010: 400 — 새 비밀번호가 현재와 동일
- AUTH_015: 400 — 회사 인증번호 유효하지 않음
- AUTH_016: 403 — 비밀번호 설정 필요
- AUTH_017: 400 — 인증 링크 만료

### Application (APPLY_XXX)
- APPLY_002: 409 — 중복 사업자 번호
- APPLY_003: 409 — 이미 처리된 신청서
- APPLY_004: 404 — 신청서 미존재
- APPLY_005: 400 — 반려 사유 필수

### Common (COMMON_XXX)
- COMMON_001: 400 — 요청 형식 오류
- COMMON_999: 500 — 서버 에러

---

**버전**: 1.0 (Draft)  
**상태**: JWT 구현 대기
