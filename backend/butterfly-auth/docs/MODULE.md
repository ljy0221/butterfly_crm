# butterfly-auth

## 역할

회원 인증, 도입 신청서 관리, 프로젝트/멤버 권한 관리, API 키, FCM 자격증명을 담당하는 모듈.

## 담당 테이블

| 테이블 | 설명 |
|--------|------|
| `members` | 운영자 계정 (role_type: ADMIN/OWNER/EDITOR/VIEWER) |
| `projects` | 프로젝트 (company_auth_no로 직원 가입 연결) |
| `applications` | 도입 신청서 (비회원 제출 → Admin 승인/반려) |
| `refresh_tokens` | JWT Refresh Token DB 저장 (RTR 방식) |
| `token_blacklist` | 로그아웃된 Access Token jti 저장 |
| `audit_logs` | 민감 액션 감사 로그 |

## API 경로

```
/api/auth/**              — 회원 인증 (공개 + JWT)
/api/applications/**      — 신청서 제출 (공개)
/api/admin/**             — Admin 전용 (ADMIN role)
/api/projects/{id}/**     — 프로젝트/멤버/키 관리 (JWT + role)
```

## 타 모듈에 제공하는 인터페이스

```java
// SDK API: X-API-Key → projectId 변환
ProjectContextService.resolveProjectIdByApiKey(String rawKey) → Long

// Management API: 멤버 권한 검증
ProjectContextService.assertMemberRole(Long memberId, Long projectId, Member.Role required)

// 감사 로그 기록 (역할 4, 6에서 의존)
AuditLogService.log(Long memberId, Long projectId, String action, ...)
```

## 의존 관계

- `butterfly-common` 의존
- Spring Security, Spring Data JPA, Spring Data Redis, Spring Mail
- JJWT 0.12.6 (JWT 발급·검증)
- AES/GCM (FCM 자격증명 암호화)

## 권한 체계

```
ADMIN  > OWNER  > EDITOR  > VIEWER
(Butterfly 운영진) (고객사 최상위) (운영자) (조회전용)
```

- `@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")` 로 선언
- 다른 모듈은 `butterfly-auth` 의존 추가 후 동일 방식 사용

## 로컬 실행 전제

```bash
cd backend
docker compose up -d          # MySQL + Redis + MailHog 기동
# MySQL: localhost:3306 / redis: localhost:6379 / mailhog: localhost:8025
```
