# Security 적용 가이드 (전파용)

> 작성일: 2026-05-11
> 작성: 역할 1 (인증/테넌트/권한)
> 수신: 역할 2(이벤트), 역할 3(세그먼트), 역할 4(캠페인), 역할 5(발송), 역할 6(리포팅)
> 참고 ADR: [ADR-0005](../adr/0005-auth-filter-placement.md)

---

## 개요

MVP 배포(5/12~5/13) 전까지 아래 전략으로 Security를 적용합니다.

- **develop**: Security OFF — 로컬 개발 시 인증 없이 API 호출 가능
- **prod 프로파일**: Security ON — JWT 필터 + `@PreAuthorize` 전부 활성화

각 팀이 할 일은 두 가지입니다.

1. **`@PreAuthorize` 추가** — 권한이 필요한 모든 컨트롤러 메서드에 선언
2. **`@RequestHeader("X-Member-Id")` → `@AuthenticationPrincipal` 교체** — prod에서 SecurityContext에서 직접 추출

> **로컬 동작 주의사항**
> 로컬(기본 프로파일)에서는 `@EnableMethodSecurity`가 비활성 상태이므로
> `@PreAuthorize`가 붙어 있어도 권한 체크가 일어나지 않습니다.
> 로컬에서 403이 뜨지 않는 것이 정상입니다.

---

## 1. 프로파일별 동작 방식

| 환경 | 실행 방식 | Security 상태 | X-Member-Id 헤더 |
|------|-----------|--------------|-----------------|
| 로컬 개발 | 기본 프로파일 (아무 옵션 없음) | OFF | 그대로 동작 |
| CI 테스트 | 기본 프로파일 | OFF | 그대로 동작 |
| MVP 배포 | `spring.profiles.active=prod` | ON | JWT에서 파싱 (헤더 무시) |

역할 1이 `SecureSecurityConfig` / `PermissiveSecurityConfig` 두 개로 분리하여 관리합니다.
각 팀은 SecurityConfig를 건드릴 필요 없이 컨트롤러 어노테이션만 작업하면 됩니다.

---

## 2. 권한 기준표

| 액션 유형 | 최소 권한 | `@PreAuthorize` 표현 |
|-----------|-----------|----------------------|
| 모든 조회 | `VIEWER` | `@projectPermission.hasRole(#projectId, 'VIEWER')` |
| 생성 · 수정 | `EDITOR` | `@projectPermission.hasRole(#projectId, 'EDITOR')` |
| 멤버 관리 · API 키 · 프로젝트 설정 변경 | `OWNER` | `@projectPermission.hasRole(#projectId, 'OWNER')` |
| `/admin/**` 경로 (플랫폼 운영) | `ADMIN` | SecurityConfig에서 일괄 처리 — 컨트롤러 불필요 |

**역할 위계 (ordinal 순):** `ADMIN(0) > OWNER(1) > EDITOR(2) > VIEWER(3)`

`hasRole(projectId, 'EDITOR')` → ADMIN, OWNER, EDITOR 통과 / VIEWER 거부

> **⚠️ ADMIN vs OWNER 혼동 주의**
> - `ADMIN`: Butterfly 플랫폼 운영자. 프로젝트에 속하지 않으며 `/api/v1/admin/**` 경로만 담당.
> - `OWNER`: 프로젝트 소유자. 멤버 관리, API 키 발급/재발급, 프로젝트 삭제 권한 보유.
> - 프로젝트 내 권한 제어에는 `OWNER` / `EDITOR` / `VIEWER`를 사용하세요.

---

## 3. @PreAuthorize 적용 패턴

### 패턴 A — 컨트롤러 전체가 같은 권한

```java
@RestController
@RequestMapping("/api/v1/projects/{projectId}/analytics")
@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")  // 전체 적용
public class AnalyticsController {

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @PathVariable Long projectId) {
        // 메서드 레벨 @PreAuthorize 불필요
    }
}
```

### 패턴 B — 메서드마다 다른 권한

```java
@RestController
@RequestMapping("/api/v1/projects/{projectId}/segments")
public class SegmentController {

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
    public ResponseEntity<ApiResponse<?>> list(@PathVariable Long projectId) { ... }

    @PostMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ResponseEntity<ApiResponse<?>> create(@PathVariable Long projectId) { ... }

    @DeleteMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ResponseEntity<ApiResponse<?>> delete(
            @PathVariable Long projectId,
            @PathVariable Long segmentId) { ... }
}
```

> **⚠️ 주의:** SpEL의 `#projectId`는 반드시 메서드 파라미터 변수명 `projectId`와 일치해야 합니다.

---

## 4. X-Member-Id 헤더 → @AuthenticationPrincipal 교체

`memberId`가 필요한 메서드에서 `@RequestHeader("X-Member-Id")`를 제거하고
`@AuthenticationPrincipal CustomUserDetails`로 교체합니다.

```java
// Before (현재 임시 방식)
@PostMapping("/{segmentId}/build")
public ResponseEntity<ApiResponse<Void>> build(
        @PathVariable Long projectId,
        @PathVariable Long segmentId,
        @RequestHeader("X-Member-Id") Long memberId) {
    service.build(segmentId, memberId);
    return ResponseEntity.ok(ApiResponse.ok());
}

// After (Security 적용 후)
@PostMapping("/{segmentId}/build")
@PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
public ResponseEntity<ApiResponse<Void>> build(
        @PathVariable Long projectId,
        @PathVariable Long segmentId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
    service.build(segmentId, userDetails.getMemberId());
    return ResponseEntity.ok(ApiResponse.ok());
}
```

**필요한 import:**
```java
import com.b101.butterfly.auth.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
```

**`memberId`가 필요 없는 조회성 메서드는 `@AuthenticationPrincipal` 없이 `@PreAuthorize`만 추가하면 됩니다.**

---

## 5. 로컬 테스트 방법 (Security 활성화 이후)

`@RequestHeader("X-Member-Id")`가 `@AuthenticationPrincipal`로 교체되면
로컬에서 JWT 없이 어떻게 테스트하냐는 의문이 생깁니다.

역할 1이 `LocalAuthFilter`를 구현해 두었습니다.
**기본 프로파일(로컬)에서만 활성화**되며, 아래 헤더를 읽어 SecurityContext를 채워줍니다.
JWT 없이도 기존처럼 헤더만 넣으면 `@AuthenticationPrincipal`이 정상 동작합니다.

| 헤더 | 설명 | 기본값 |
|------|------|--------|
| `X-Member-Id` | 행위자 회원 ID | 없으면 인증 미설정 |
| `X-Project-Id` | 프로젝트 ID | `null` |
| `X-Role` | 역할 (`ADMIN`/`OWNER`/`EDITOR`/`VIEWER`) | `VIEWER` |

**로컬 테스트 예시:**
```http
GET /api/v1/projects/1/segments
X-Member-Id: 42
X-Project-Id: 1
X-Role: EDITOR
```

> **prod 프로파일에서는 LocalAuthFilter가 비활성화**됩니다.
> prod에서는 반드시 `Authorization: Bearer {JWT}` 헤더로 요청해야 합니다.

---

## 6. API 경로별 인증 방식 (SecurityConfig 기준)

| 경로 패턴 | 인증 방식 | 컨트롤러 작업 필요 여부 |
|-----------|-----------|------------------------|
| `/api/v1/auth/login`, `/signup`, `/refresh` 등 | 없음 (공개) | 불필요 |
| `/api/v1/applications` | 없음 (공개) | 불필요 |
| `/api/v1/admin/**` | JWT + `ROLE_ADMIN` 자동 적용 | 불필요 |
| `/api/v1/projects/{projectId}/**` | JWT 인증 + `@PreAuthorize` | **각 컨트롤러에 추가 필요** |
| `/sdk/**` | API Key (`X-Api-Key` 헤더) | 불필요 (ApiKeyFilter 처리) |

공개 경로가 추가로 필요하면 역할 1에게 요청하세요. SecurityConfig에 추가합니다.

---

## 7. 모듈별 체크리스트

MVP 배포 브랜치 생성 전까지 각 모듈에서 완료해야 할 항목입니다.

```
[ ] /api/v1/projects/{projectId}/** 경로의 모든 엔드포인트에 @PreAuthorize 선언
[ ] @RequestHeader("X-Member-Id") 전부 제거 → @AuthenticationPrincipal 교체
    (memberId가 필요한 메서드만 해당, 조회 전용은 불필요)
[ ] API 경로에 /api/v1 prefix 포함 여부 확인
[ ] 공개 경로 추가 필요 시 역할 1에게 알려서 SecurityConfig에 추가 요청
```

---

## 8. 현재 모듈별 적용 현황

| 모듈 | 컨트롤러 | @PreAuthorize | X-Member-Id | 조치 필요 |
|------|----------|---------------|-------------|-----------|
| butterfly-auth | AuthController | 불필요 (공개/인증 경로 분리) | ✅ 교체 예정 (역할 1) | 역할 1 담당 |
| butterfly-tenant | ProjectController | ❌ 없음 | ❌ 있음 | 역할 1 담당 |
| butterfly-tenant | ApplicationController | ❌ 없음 (`/admin/**`은 SecurityConfig 처리) | ❌ 있음 (approve/reject) | 역할 1 담당 |
| butterfly-segment | SegmentController | ✅ 일부 적용 | 확인 필요 | X-Member-Id 교체 확인 |
| butterfly-reporting | AnalyticsController | ✅ 클래스 레벨 | 확인 필요 | X-Member-Id 교체 확인 |
| butterfly-reporting | AuditLogController | 확인 필요 | 확인 필요 | 역할 6 확인 |
| butterfly-reporting | ReconciliationController | 확인 필요 | 확인 필요 | 역할 6 확인 |
| butterfly-notification | ChannelCredentialController | ❌ 없음 | 확인 필요 | 역할 5 추가 필요 |
| butterfly-notification | NotificationLogController | 확인 필요 | 확인 필요 | 역할 5 확인 |
| butterfly-event | EventCollectController | 불필요 (SDK 경로) | 해당 없음 | API Key 방식 별도 |
| butterfly-event | EventManagementController | 확인 필요 | 확인 필요 | 역할 2 확인 |

---

## 9. 역할 1이 담당하는 작업 (참고용)

아래 작업은 역할 1이 직접 진행합니다. 각 팀은 자기 모듈만 신경 쓰면 됩니다.

- `SecurityConfig` → `PermissiveSecurityConfig` (기본) / `SecureSecurityConfig` (prod) 분리
- `JwtAuthenticationFilter`, `ApiKeyAuthenticationFilter` prod 프로파일에 등록
- `Http401AuthenticationEntryPoint`, `Http403AccessDeniedHandler` 구현
- `AuthController`, `ProjectController`, `ApplicationController` 어노테이션 작업

---

## 10. 문의

- 권한 기준 불명확 / SecurityConfig 경로 추가 요청: **역할 1**
- `CustomUserDetails` 필드 추가 필요 시: **역할 1**
