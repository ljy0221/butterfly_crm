# butterfly-common

## 역할

모든 도메인 모듈이 공통으로 사용하는 인프라 코드를 제공하는 라이브러리 모듈.

## 주요 기능

- **BaseEntity**: 모든 엔티티의 부모 클래스. `createdAt`, `updatedAt`을 `@PrePersist`/`@PreUpdate`로 자동 관리
- **ApiResponse**: 공통 API 응답 포맷 (`success`, `data`, `error`)
- **ErrorResponse**: 실패 응답의 `error` 필드 구조 (`code`, `message`)
- **PageResponse**: 페이지네이션 응답 포맷
- **ErrorCode**: 도메인별 에러 코드 enum이 구현해야 하는 인터페이스
- **CommonErrorCode**: 전 도메인 공통 에러 코드 (INVALID_REQUEST, UNAUTHORIZED, FORBIDDEN 등)
- **BusinessException**: 비즈니스 예외. `ErrorCode`와 함께 던지면 GlobalExceptionHandler가 자동 변환
- **GlobalExceptionHandler**: `@RestControllerAdvice` 기반 전역 예외 처리

## 의존 관계

- 다른 모듈에 의존하지 않음 (최하위 모듈)
- 모든 도메인 모듈이 이 모듈에 의존
