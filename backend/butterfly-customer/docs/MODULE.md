# butterfly-customer

## 역할

고객(유저) 데이터 수집 및 관리를 담당하는 모듈. SDK를 통해 클라이언트 앱/웹에서 호출되는 API와 관리자용 조회 API를 제공한다.

## 주요 기능

- **유저 식별/생성**: SDK에서 로그인 시 호출하여 유저를 식별하거나 신규 생성
- **디바이스 등록**: FCM 토큰 등 디바이스 정보 등록/갱신
- **이벤트 수집**: 단건/배치 이벤트 수집 (큐 적재 후 즉시 응답)
- **유저 프로필 관리**: 유저 목록 조회, 상세 조회, 태그 관리
- **유저 이벤트 조회**: 특정 유저의 이벤트 이력 조회

## 담당 테이블

`users`, `devices`, `events`, `events_category`, `user_tags`

## API 경로

- `/sdk/v1/**` — SDK API (API Key 인증)
- `/api/projects/{projectId}/users/**` — Management API (JWT 인증)

## 의존 관계

- `butterfly-common`, `butterfly-auth`에 의존
- Spring Data JPA, MySQL
