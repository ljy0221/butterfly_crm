---
status: accepted
date: 2026-04-21
decision-makers: 역할 1 (인증/테넌트/프로젝트/권한)
consulted: 역할 2~6 (모든 도메인 컨트롤러 보유 팀)
---

# ADR-0005: 인증/인가 필터 배치 및 권한 선언 방식

## Context and Problem Statement

requirements 4-5("모든 API는 `project_id` 기준 접근 권한 검증")를 만족하려면
모든 Management API가 다음을 거쳐야 한다:

1. JWT 파싱 및 유효성 검증 → 행위자(member) 식별
2. URL path의 `projectId`에 대한 멤버의 역할 조회 (`project_members`)
3. 엔드포인트별 최소 권한(`VIEWER` / `EDITOR` / `ADMIN` / `OWNER`)과 비교

이 로직을 **각 도메인 컨트롤러가 개별 구현**하면:

- 구현 편차로 인한 보안 허점 (한 도메인에서 권한 체크 누락)
- 매 요청마다 `project_members` 테이블 조회 → DB 부담
- 권한 규칙 변경 시 모든 도메인 수정 필요

MVP 일정 안에서 **전 도메인 일관성**을 확보할 방식이 필요하다.

## Decision Drivers

- 컨트롤러 코드에서 권한 선언이 **단순·일관**해야 함
- 권한 조회 비용이 DB 부담으로 직결되지 않아야 함
- `member → projects` 캐시가 역할 변경 시 즉시 반영되어야 함 (정합성)
- Spring Security 표준을 벗어나지 않기 (팀 학습곡선 최소화)

## Considered Options

### 권한 선언 방식

| 방식 | 장점 | 단점 |
|------|------|------|
| 커스텀 애너테이션 + AOP | 도메인 친화적 | AOP 설정/테스트 인프라 자체 구현 필요 |
| **`@PreAuthorize` (SpEL)** | Spring Security 표준 | SpEL 문법 숙지 필요 |
| 서비스 계층 수동 호출 | 유연 | 구현이 도메인마다 달라져 일관성 붕괴 |

### 필터 배치

| 방식 | 장점 | 단점 |
|------|------|------|
| **`butterfly-auth` 제공, 타 모듈이 implementation 의존** | 단일 구현, 교체 쉬움 | auth가 가진 JPA/Redis 의존이 API에도 노출 |
| `butterfly-api`에 직접 구현 | API 진입점에 가까움 | Worker 등에서 재사용 불가 |
| 별도 `butterfly-security` 모듈 신설 | 관심사 분리 극대화 | 모듈 수 증가, MVP 과설계 |

## Decision Outcome

### 1. 권한 선언 — `@PreAuthorize` 통일

전 도메인 컨트롤러가 Spring Security의 `@PreAuthorize(SpEL)`만 쓴다.
각 엔드포인트는 애너테이션 한 줄로 최소 권한을 선언.

```java
@GetMapping("/analytics/dashboard")
@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
public ApiResponse<DashboardResponse> getDashboard(@PathVariable Long projectId, ...) { ... }
```

- `@projectPermission`은 `butterfly-auth`가 제공하는 권한 평가 빈 (`ProjectPermissionEvaluator`)
- SpEL에서 `#projectId` 경로 변수를 참조해 현재 멤버의 역할을 조회/비교

**역할 위계**: `OWNER > ADMIN > EDITOR > VIEWER`. `hasRole(projectId, 'VIEWER')`는 VIEWER **이상** 권한을 의미.

### 2. 필터 위치 — `butterfly-auth` 제공

`butterfly-auth` 모듈에 두 개의 Spring Security 필터를 빈으로 등록:

- **`JwtAuthenticationFilter`** — Authorization 헤더의 JWT를 파싱, `SecurityContext`에 `Authentication` 주입. 실패 시 401
- **`ProjectPermissionEvaluator`** — `@PreAuthorize`가 SpEL에서 호출하는 빈. 경로 `{projectId}`와 현재 인증 사용자의 역할을 비교

각 도메인은 `implementation project(':butterfly-auth')` 의존 한 줄로 자동 활성화된다(Spring Boot 자동 설정).

### 3. 역할별 최소 권한 기준선

| 액션 범주 | 최소 권한 |
|---------|----------|
| 모든 조회성 API | VIEWER |
| 캠페인/세그먼트 생성·수정 | EDITOR |
| 멤버 초대/제거, 역할 변경, 캠페인 발송 취소 | ADMIN |
| 프로젝트 삭제, API 키 폐기, FCM 자격증명 수정 | OWNER |

엔드포인트별 세부 권한은 각 도메인 API 문서(01~06)가 명시.

### 4. 역할 조회 캐싱 — Redis TTL 5분 + 변경 시 즉시 무효화

```
Key: auth:permission:{memberId}:{projectId}
Value: { "role": "ADMIN", "cachedAt": "..." }
TTL: 300s
```

- `@PreAuthorize` 평가 시 Redis 우선 조회, miss면 `project_members` 테이블 조회 후 캐싱
- 역할 변경 API(`PUT /api/projects/{projectId}/members/{memberId}/role`)는 처리 직후 해당 키를 `DEL`
- 멤버 제거 API도 동일하게 캐시 무효화

**TTL 5분은 의도적 상한**: 캐시 무효화가 실패해도 5분 내 자동 복구. 정합성과 성능의 절충.

### 구현 체크리스트 (역할 1 주도, 타 도메인 동반 합의)

- [ ] `butterfly-auth`에 `JwtAuthenticationFilter` + Spring Security 설정
- [ ] `ProjectPermissionEvaluator` 빈 구현 (SpEL 함수 노출)
- [ ] Redis 기반 `PermissionCache` 구현
- [ ] 역할 변경/멤버 제거 API가 캐시 무효화를 반드시 호출하는지 테스트
- [ ] 컨트롤러 코드 리뷰 체크리스트에 "모든 엔드포인트에 `@PreAuthorize` 선언" 추가
- [ ] `@PreAuthorize` 평가 실패 시 응답 포맷이 `ApiResponse.fail(COMMON_003 FORBIDDEN)` 형태를 유지하는지 확인
  (Spring Security 기본 응답은 00-common 규약과 다르므로 `AccessDeniedHandler` 커스터마이즈 필요)

## Consequences

### 좋은 점

- 컨트롤러 코드가 간결 — 권한 선언이 애너테이션 1줄
- Spring Security 표준 준수 → 외부 자료/레퍼런스 풍부
- 캐시로 DB 부담 완화, TTL 5분으로 장애 시 자동 복구
- 단일 필터 구현 → 보안 규칙 변경이 한 곳에서 끝남

### 나쁜 점 / 주의

- `butterfly-auth`가 `butterfly-common` 바로 위 레이어에 고정 — 순환 의존 방지를 위해 `butterfly-auth`는 타 도메인에 의존하면 안 됨 (모든 도메인이 auth에 의존하는 단방향 유지)
- `@PreAuthorize`는 메서드 진입 시점에 평가되므로 **서비스 계층 내부에서의 권한 재검증**이 필요한 케이스(도메인 간 호출 등)는 별도 수단 필요
- Redis 장애 시 매 요청이 DB를 타는 fallback — 모니터링 지표(`auth.permission.cache_miss_ratio`) 수집 권장
- `AccessDeniedHandler` / `AuthenticationEntryPoint` 커스터마이즈 누락 시 에러 응답 포맷 불일치 → 통합 테스트 필수

## More Information

- 관련 ADR: [ADR-0004 감사 로그 아키텍처](0004-audit-log-architecture.md)
- 제안서 §6 (체크리스트 항목 13): [backend/docs/proposals/01-erd-api-review.md](../proposals/01-erd-api-review.md)
- requirements 3-1, 4-3, 4-5
- 권한 역할 표: [backend/docs/api/00-common.md](../api/00-common.md)
