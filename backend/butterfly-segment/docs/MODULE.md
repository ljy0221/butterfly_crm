# butterfly-segment

## 역할

조건 기반 고객 세그먼트(그룹) 생성 및 관리를 담당하는 모듈. 규칙(rule)을 조합하여 대상 유저를 분류한다.

## 주요 기능

- **세그먼트 CRUD**: 세그먼트 생성/조회/수정/삭제
- **규칙 관리**: 태그, 이벤트 등 조건 기반 규칙을 조합하여 세그먼트 정의 (ADR-0001)
- **미리보기**: 저장 전 조건으로 예상 모수 계산
- **대상 유저 조회**: 세그먼트에 해당하는 유저 목록 페이지네이션 조회

## 담당 테이블

`segments`, `segment_rules`

## API 경로

- `/api/projects/{projectId}/segments/**` — 세그먼트 관리 (JWT 인증)

## 의존 관계

- `butterfly-common`, `butterfly-auth`, `butterfly-customer`에 의존
- Spring Data JPA, Spring Data Redis, MySQL
