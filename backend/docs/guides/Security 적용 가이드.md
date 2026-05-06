# 보안 통합 적용 가이드 (전파용)

> 작성일: 2026-05-07  
> 작성: 역할 1 (인증/테넌트/권한)  
> 수신: 역할 2(이벤트), 역할 3(세그먼트), 역할 4(캠페인), 역할 5(발송), 역할 6(리포팅)  
> 참고 ADR: [ADR-0005](../adr/0005-auth-filter-placement.md)

---

## 공지 요약

`butterfly-auth`에 JWT 인증 필터 구현이 완료되면, 각 도메인 모듈에서 아래 두 가지 작업이 필요합니다.

1. **`@PreAuthorize` 추가** — 권한이 필요한 모든 컨트롤러 메서드에 선언
2. **`@RequestHeader("X-Member-Id")` 제거** — `@AuthenticationPrincipal`로 교체

---

## 1. 권한 기준표

| 액션 유형 | 최소 권한 | `@PreAuthorize` 표현 |
|---|---|---|
| 모든 조회 | `VIEWER` | `@projectPermission.hasRole(#projectId, 'VIEWER')` |
| 생성 · 수정 | `EDITOR` | `@projectPermission.hasRole(#projectId, 'EDITOR')` |
| 멤버 관리 · 설정 변경 | `ADMIN` | `@projectPermission.hasRole(#projectId, 'ADMIN')` |
| 프로젝트 삭제 · API 키 관리 | `OWNER` | `@projectPermission.hasRole(#projectId, 'OWNER')` |

**역할 위계:** `ADMIN > OWNER > EDITOR > VIEWER`  
`hasRole(projectId, 'EDITOR')` → ADMIN, OWNER, EDITOR 통과 / VIEWER 거부

---

## 2. @PreAuthorize 적용

### 패턴 A — 컨트롤러 전체가 같은 권한

```java
@RestController
@RequestMapping("/api/v1/projects/{projectId}/analytics")
@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")  // 전체 적용
public class AnalyticsController {

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(@PathVariable Long projectId) {
        // 메서드 레벨 @PreAuthorize 불필요
    }

    @GetMapping("/campaigns")
    public ApiResponse<?> getCampaigns(@PathVariable Long projectId) {
        // 동일하게 VIEWER 적용
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
    public ApiResponse<?> list(@PathVariable Long projectId) { ... }

    @PostMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'EDITOR')")
    public ApiResponse<?> create(@PathVariable Long projectId) { ... }

    @DeleteMapping("/{segmentId}")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'ADMIN')")
    public ApiResponse<?> delete(@PathVariable Long projectId, @PathVariable Long segmentId) { ... }
}
```

> **⚠️ 주의:** SpEL의 `#projectId`는 반드시 메서드 파라미터 변수명 `projectId`와 일치해야 합니다.

---

## 3. X-Member-Id 헤더 → @AuthenticationPrincipal 교체

JWT 필터 구현 완료 후 임시 헤더를 제거하고 SecurityContext에서 직접 꺼냅니다.

```java
// Before (현재 임시 방식)
@GetMapping
public ApiResponse<?> list(
        @PathVariable Long projectId,
        @RequestHeader("X-Member-Id") Long memberId) {
    return ApiResponse.ok(service.list(projectId, memberId));
}

// After (교체 후)
@GetMapping
@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
public ApiResponse<?> list(
        @PathVariable Long projectId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long memberId   = userDetails.getMemberId();
    Long projectId2 = userDetails.getProjectId();  // 필요 시
    return ApiResponse.ok(service.list(projectId, memberId));
}
```

**필요한 import:**
```java
import com.b101.butterfly.auth.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
```

---

## 4. API 경로별 인증 방식

| 경로 패턴 | 인증 방식 | 처리 주체 |
|---|---|---|
| `/api/v1/auth/**` | 없음 (공개) | SecurityConfig |
| `/api/v1/applications` | 없음 (공개) | SecurityConfig |
| `/api/v1/admin/**` | JWT + `ROLE_ADMIN` | SecurityConfig |
| `/api/v1/projects/{projectId}/**` | JWT + `@PreAuthorize` | 각 컨트롤러 |
| `/sdk/**` | API Key (`X-Api-Key`) | ApiKeyFilter (별도) |

---

## 5. 모듈별 체크리스트

JWT 필터 통합 완료 공지 후 각 모듈에서 확인할 항목입니다.

```
[ ] /api/v1/projects/{projectId}/** 경로의 모든 엔드포인트에 @PreAuthorize 선언
[ ] @RequestHeader("X-Member-Id") 전부 제거 → @AuthenticationPrincipal 교체
[ ] API 경로에 /api/v1 prefix 포함 여부 확인
[ ] 인증이 불필요한 공개 경로가 있다면 역할 1에게 알려서 SecurityConfig에 추가 요청
```

---

## 6. 현재 모듈별 적용 현황

| 모듈 | 컨트롤러 | @PreAuthorize | 조치 필요 |
|---|---|---|---|
| butterfly-segment | SegmentController | ✅ 적용됨 | X-Member-Id 제거 |
| butterfly-reporting | AnalyticsController | ✅ 클래스 레벨 | - |
| butterfly-reporting | AuditLogController | 확인 필요 | - |
| butterfly-reporting | ReconciliationController | 확인 필요 | - |
| butterfly-notification | ChannelCredentialController | ❌ 없음 | @PreAuthorize 추가 + `/api/v1` prefix 추가 |
| butterfly-notification | NotificationLogController | 확인 필요 | - |
| butterfly-tenant | ProjectController | ❌ 없음 | @PreAuthorize 추가 |
| butterfly-event | EventCollectController | SDK 경로 | API Key 방식 별도 논의 |

---

## 7. 문의

- 권한 기준 / SecurityConfig 추가 요청: **역할 1**
- `CustomUserDetails` 필드 추가 필요 시: **역할 1**
- 세부 구현 내용: [security-integration-guide.md](./security-integration-guide.md)
