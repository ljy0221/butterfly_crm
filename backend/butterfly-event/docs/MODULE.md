# butterfly-event

## 역할

고객(유저) 데이터 수집 및 관리를 담당하는 모듈. SDK를 통해 클라이언트 앱/웹에서 호출되는 API와 관리자용 조회 API를 제공한다.

## 주요 기능

- **유저 식별/생성**: SDK에서 로그인 시 호출하여 유저를 식별하거나 신규 생성
- **디바이스 등록**: FCM 토큰 등 디바이스 정보 등록/갱신
- **이벤트 수집 (producer)**: SDK 이벤트에 `event_uuid` 부여 후 Kafka 토픽 `events.collected`로 발행, 즉시 응답 (`api` 프로파일에서 활성화)
- **이벤트 적재 (consumer)**: `events.collected` 토픽을 컨슘하여 `events` 테이블에 적재 (`worker` 프로파일에서 활성화, `event_uuid` UNIQUE 컬럼으로 멱등 보장)
- **이벤트 일자별 집계**: events insert와 동시에 `event_daily_agg` 증분 (events 신규 row일 때만 +1, 멱등 보장)
- **이벤트 카테고리/스키마 관리**: 카테고리 정의 + 스키마 버전 이력 관리
- **유저 프로필 관리**: 유저 목록 조회, 상세 조회, 태그 관리
- **유저 이벤트 조회**: 특정 유저의 이벤트 이력 조회

> 이벤트 수집 흐름과 producer/consumer 통합 결정의 근거는 [ADR-0011](../../docs/adr/0011-event-collection-flow.md) 참조.

## 담당 테이블

- **유저/디바이스**: `users`, `devices`, `user_tags`
- **이벤트**: `events`, `event_categories`, `event_schemas`, `event_schema_fields`
- **집계**: `event_daily_agg`

## API 경로

- `/sdk/v1/**` — SDK API (API Key 인증)
- `/api/projects/{projectId}/users/**` — Management API (JWT 인증)

## 의존 관계

- `butterfly-common`, `butterfly-auth`에 의존
- Spring Data JPA, MySQL
- Spring Kafka (이벤트 수집 producer/consumer)
