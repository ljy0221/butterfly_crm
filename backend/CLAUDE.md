# Butterfly Backend

## 1. 프로젝트 개요

한국형 멀티테넌트 CRM 캠페인 SaaS.
고객 행동 데이터 수집 → 세그먼트 계산 → 캠페인/플로우 실행 → 성과 분석을 하나의 플랫폼으로 묶은 CRM 운영 엔진.

## 2. 기술 스택

- Java 21, Spring Boot 3.5, Gradle 멀티모듈
- Spring Data JPA + MySQL
- Spring Security (JWT 인증 + 프로젝트별 역할 기반 인가)
- Spring AMQP (RabbitMQ) + Spring Kafka
- Spring Data Redis
- Lombok

## 3. 모듈 구조

```
butterfly (root)
├── butterfly-common     # 공통 코드 (BaseEntity, ApiResponse, ErrorCode, GlobalExceptionHandler)
├── butterfly-auth       # 인증/인가 (JWT, Spring Security, 프로젝트 권한)
├── butterfly-customer   # 고객 도메인 (고객 데이터 수집/관리)
├── butterfly-segment    # 세그먼트 도메인 (조건 트리 기반 고객 분류)
├── butterfly-campaign   # 캠페인 도메인 (캠페인 정의/실행 분리 — ADR-0002)
├── butterfly-delivery   # 발송 도메인 (앱 푸시, 웹 푸시 등 채널별 발송)
├── butterfly-analytics  # 분석 도메인 (대시보드, 캠페인/채널/세그먼트 통계)
├── butterfly-api        # API 게이트웨이 (bootJar, 모든 도메인 모듈 조립)
└── butterfly-worker     # 비동기 워커 (bootJar, 메시지 소비/발송 처리)
```

의존 방향: `butterfly-api / butterfly-worker` → 도메인 모듈들 → `butterfly-common`

## 4. 패키지 컨벤션

- 기본 패키지: `com.b101.butterfly`
- 공통 모듈: `com.b101.butterfly.global.{error,response,entity}`
- 도메인 모듈: `com.b101.butterfly.<도메인>.{controller,service,repository,dto,entity,exception}`
- 도메인별 에러 코드 enum은 `exception` 패키지에 위치 (예: `AnalyticsErrorCode`)

## 5. 코딩 컨벤션

- **커밋 메시지**: `<type>(<모듈명>): <설명>` (예: `feat(common): BaseEntity 추가`)
  - type: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`
- **API 응답**: 모든 응답은 `ApiResponse<T>`로 래핑
  - 성공: `ApiResponse.ok(data)`
  - 실패: `ApiResponse.fail(code, message)`
- **예외 처리**: `BusinessException(ErrorCode)` → `GlobalExceptionHandler`가 자동 변환
- **인가**: 컨트롤러에 `@PreAuthorize("@projectPermission.hasRole(#projectId, 'ROLE')")` 사용 (ADR-0005)
- **DTO**: record 사용 권장
- **Lombok**: `@Getter`, `@RequiredArgsConstructor` 등 적극 사용, `@Data` 지양

## 6. 공통 규칙

- 모든 엔티티는 `BaseEntity`를 상속하여 `createdAt`, `updatedAt`을 자동 관리 (`@PrePersist` / `@PreUpdate`)
- 에러 코드는 도메인별 enum으로 분리하고 `ErrorCode` 인터페이스를 구현 (prefix로 도메인 구분: `AUTH_001`, `ANALYTICS_001`)
- 공통 에러는 `CommonErrorCode`에 정의
- API 경로 패턴: `/api/projects/{projectId}/...` (멀티테넌트 프로젝트 스코프)
- 타임존: Asia/Seoul 서버 고정
- ADR 문서는 `docs/adr/` 디렉토리에서 관리
