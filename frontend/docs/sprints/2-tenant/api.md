# Sprint 2 — Tenant (Project/Member/API Key) API 명세

> Backend Liaison Agent가 정리한 백엔드 → 프론트엔드 계약서.  
> **작성 일시**: 2026-05-01  
> **백엔드 버전**: Spring Boot 3.5.13  
> **상태**: 권한 검증 TODO — 현재 컨트롤러에 @PreAuthorize 미구현 (라우터에서 관리 필요)

---

## 1. 도메인 개요 및 권한 매트릭스

### 1.1 엔티티 관계도

`
Member (butterfly-auth)
├─ projectId: Long      ← 단일 프로젝트만 소속 (1:1 매핑)
├─ roleType: RoleType   ← ADMIN | OWNER | EDITOR | VIEWER
└─ email, name, phone, birthDate

Project (butterfly-tenant)
├─ id: Long
├─ name, businessNo, companyAuthNo
├─ ownerId: Long        ← Member.id 참조 (프로젝트 소유자)
├─ publicApiKey: String (발급 후 1회만 평문 노출)
├─ plan: ProjectPlan    ← FREE | BASIC | PRO | ENTERPRISE
└─ dailyLimit: Integer
`

### 1.2 권한 매트릭스

| 엔드포인트 | 공개 | VIEWER | EDITOR | OWNER | ADMIN |
|---|:---:|:---:|:---:|:---:|:---:|
| GET /api/v1/projects/{projectId} | ❌ | ✅ | ✅ | ✅ | ✅ |
| DELETE /api/v1/admin/projects/{projectId} | ❌ | ❌ | ❌ | ❌ | ✅ |
| GET /api/v1/projects/{projectId}/members | ❌ | ✅ | ✅ | ✅ | ✅ |
| DELETE /api/v1/projects/{projectId}/members/{memberId} | ❌ | ❌ | ❌ | ✅ | ✅ |
| PUT /api/v1/projects/{projectId}/members/{memberId}/role | ❌ | ❌ | ❌ | ✅ | ✅ |
| POST /api/v1/projects/{projectId}/api-keys/issue | ❌ | ❌ | ❌ | ✅ | ✅ |
| POST /api/v1/projects/{projectId}/api-keys/revoke | ❌ | ❌ | ❌ | ✅ | ✅ |

---

## 2. 프로젝트 진입 플로우 — 프론트엔드 구현 가이드

### 2.1 현재 아키텍처 (Member 단일 projectId)

**핵심**: 모든 Member는 정확히 1개의 프로젝트에만 소속.

`
POST /api/v1/auth/login
└─ response.member.projectId: 456
`

**결론**:
- /projects 페이지는 **불필요**.
- 로그인 후 member.projectId를 즉시 store에 저장 → /p/:projectId/settings 또는 /p/:projectId/campaign으로 자동 redirect.
- GET /api/v1/auth/me에서도 member.projectId 반환 → 동일 로직.

### 2.2 로그인 응답 구조

`json
{
  "success": true,
  "data": {
    "accessToken": "",           // JWT 미구현 — 빈 문자열
    "refreshToken": "",
    "member": {
      "memberId": 123,
      "email": "user@example.com",
      "name": "홍길동",
      "phone": "01012345678",
      "roleType": "VIEWER",
      "projectId": 456,           // ← 단일 프로젝트 ID (필수)
      "createdAt": "2026-05-01T10:00:00"
    }
  },
  "error": null
}
`

### 2.3 인증 헤더 (현재 임시)

`
Authorization: Bearer <accessToken>  // 빈 문자열이면 미첨부 가능 (frontend client.ts에서 관리)
X-Member-Id: 123                      // 임시 헤더 (JWT 완성 전 사용)
`

---

## 3. Project 엔드포인트

### 3.1 GET /api/v1/projects/{projectId}

프로젝트 상세 정보 조회 (이름, 회사, owner, API 키 보유 여부, 플랜, 일일 한도).

**인증**: 필수 (X-Member-Id)  
**권한**: VIEWER 이상  
**HTTP**: 200 OK

**요청**:
`
GET /api/v1/projects/456
X-Member-Id: 123
`

**응답**:
`json
{
  "success": true,
  "data": {
    "projectId": 456,
    "name": "버터플라이 마케팅팀",
    "businessNo": "1234567890",
    "companyAuthNo": "98765432100000",
    "ownerId": 123,
    "hasApiKey": true,
    "plan": "FREE",
    "dailyLimit": 1000
  },
  "error": null
}
`

**필드 설명**:
- ownerId: 프로젝트 owner의 Member ID (이메일이 아닌 ID)
- hasApiKey: API 키 발급 여부 (평문 키는 발급 직후만 노출되므로, 이후 조회는 boolean만)
- plan: FREE | BASIC | PRO | ENTERPRISE
- dailyLimit: 일일 API 호출 한도 (널 가능)

**에러 케이스**:
- 404 NOT_FOUND — PROJ_002: 프로젝트 미존재
- 403 FORBIDDEN — PROJ_004: 접근 권한 없음

**UI 가이드**:
- Settings > "프로젝트 정보" 탭에서 노출
- owner 정보 표시 시 ownerId로 member 목록에서 이름 매핑

---

## 4. Member 엔드포인트

### 4.1 GET /api/v1/projects/{projectId}/members

프로젝트 멤버 목록 조회.

**인증**: 필수 (X-Member-Id)  
**권한**: VIEWER 이상  
**HTTP**: 200 OK

**요청**:
`
GET /api/v1/projects/456/members
X-Member-Id: 123
`

**응답**:
`json
{
  "success": true,
  "data": [
    {
      "memberId": 123,
      "name": "홍길동",
      "email": "hong@example.com",
      "phone": "01012345678",
      "birthDate": "1990-05-15",
      "roleType": "OWNER"
    },
    {
      "memberId": 124,
      "name": "김철수",
      "email": "kim@example.com",
      "phone": "01087654321",
      "birthDate": "1985-03-20",
      "roleType": "EDITOR"
    }
  ],
  "error": null
}
`

**필드 설명**:
- 페이지네이션 없음 (단일 리스트로 모든 멤버 반환)
- roleType: ADMIN | OWNER | EDITOR | VIEWER

**UI 가이드**:
- Settings > "멤버 관리" 탭에서 테이블 형태로 노출
- 현재 사용자의 roleType에 따라 "삭제", "권한 변경" 버튼 표시 여부 결정 (OWNER만)

---

### 4.2 DELETE /api/v1/projects/{projectId}/members/{memberId}

멤버 삭제 (Soft Delete — 회원 탈퇴가 아닌 프로젝트 퇴출).

**인증**: 필수 (X-Member-Id)  
**권한**: OWNER만  
**HTTP**: 200 OK

**요청**:
`
DELETE /api/v1/projects/456/members/124
X-Member-Id: 123
`

**비즈니스 로직**:
1. 대상 멤버가 OWNER 역할이면 삭제 불가
2. 대상 멤버의 is_deleted = true, deletedAt 설정
3. 대상 멤버의 Refresh Token 삭제 (로그아웃 강제)

**에러 케이스**:
- 404 NOT_FOUND — PROJ_002: 프로젝트 미존재
- 404 NOT_FOUND — PROJ_006: 멤버가 이 프로젝트에 소속되지 않음
- 409 CONFLICT — PROJ_007: OWNER는 삭제 불가
- 403 FORBIDDEN — PROJ_004: 삭제 권한 없음 (OWNER가 아닌 경우)

---

### 4.3 PUT /api/v1/projects/{projectId}/members/{memberId}/role

멤버 권한 변경 (역할 업그레이드/다운그레이드).

**인증**: 필수 (X-Member-Id)  
**권한**: OWNER만  
**HTTP**: 200 OK

**요청**:
`json
PUT /api/v1/projects/456/members/124/role
X-Member-Id: 123

{
  "roleType": "EDITOR"
}
`

**권한 변경 규칙**:
- OWNER → 다른 역할로 강등 불가 (PROJ_008)
- EDITOR ↔ VIEWER: 자유

**에러 케이스**:
- 404 NOT_FOUND — PROJ_002: 프로젝트 미존재
- 404 NOT_FOUND — PROJ_006: 멤버가 이 프로젝트에 소속되지 않음
- 409 CONFLICT — PROJ_008: OWNER 역할 이전은 별도 절차 필요
- 403 FORBIDDEN — PROJ_004: 변경 권한 없음

---

## 5. API Key 엔드포인트

### 5.1 POST /api/v1/projects/{projectId}/api-keys/issue

API 키 최초 발급.

**인증**: 필수 (X-Member-Id)  
**권한**: OWNER만  
**HTTP**: 200 OK

**요청**:
`
POST /api/v1/projects/456/api-keys/issue
X-Member-Id: 123
`

**응답**:
`json
{
  "success": true,
  "data": {
    "publicApiKey": "pk_live_a1b2c3d4e5f6g7h8i9j0k1l2"
  },
  "error": null
}
`

**키 형식**:
`
"pk_live_" + 24자 랜덤 16진수 = 32자 총길이
`

**비즈니스 로직**:
1. 프로젝트에 이미 publicApiKey가 있으면 PROJ_005 에러
2. 새로운 키 생성
3. Project.publicApiKey 저장

**에러 케이스**:
- 404 NOT_FOUND — PROJ_002: 프로젝트 미존재
- 409 CONFLICT — PROJ_005: 이미 발급된 키 존재 (revoke로 재발급)
- 403 FORBIDDEN — PROJ_004: 발급 권한 없음 (OWNER가 아닌 경우)

**UI 가이드**:
- Settings > "API 키" 탭 > "발급" 버튼 (OWNER만)
- 응답 받은 키를 **1회 복사 모달**로 표시 (이후 조회 불가)

---

### 5.2 POST /api/v1/projects/{projectId}/api-keys/revoke

API 키 재발급 (기존 키 폐기 + 새 키 생성).

**인증**: 필수 (X-Member-Id)  
**권한**: OWNER만  
**HTTP**: 200 OK

**응답**:
`json
{
  "success": true,
  "data": {
    "publicApiKey": "pk_live_x9y8z7w6v5u4t3s2r1q0p9o8"
  },
  "error": null
}
`

**UI 가이드**:
- Settings > "API 키" 탭 > "재발급" 버튼
- 경고: "기존 키는 즉시 무효화됩니다."
- 새 키 **1회 복사 모달** 표시

---

## 6. 권한 검증 상태

⚠️ **중요**: 백엔드 컨트롤러에 @PreAuthorize 어노테이션 미구현.

**프론트엔드 대응**:
1. 라우터 가드에서 member.roleType 검증
2. 버튼 비활성화 (권한 없는 사용자)

---

## 7. 에러 코드 (PROJ_XXX)

- PROJ_002: 404 — 프로젝트 미존재
- PROJ_003: 409 — 진행 중인 캠페인으로 인해 삭제 불가
- PROJ_004: 403 — 접근 권한 없음
- PROJ_005: 409 — 이미 발급된 API 키 존재
- PROJ_006: 404 — 멤버가 이 프로젝트에 소속되지 않음
- PROJ_007: 409 — OWNER는 삭제 불가
- PROJ_008: 409 — OWNER 권한 이전은 별도 절차 필요

---

**버전**: 1.0  
**상태**: 권한 검증 TODO (백엔드 @PreAuthorize 구현 대기)  
**마지막 업데이트**: 2026-05-01
