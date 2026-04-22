# butterfly-campaign

## 역할

캠페인의 생성, 수정, 발송 트리거를 담당하는 모듈. 캠페인 정의와 실행을 분리하여 관리한다 (ADR-0002).

## 주요 기능

- **캠페인 CRUD**: 캠페인 생성(DRAFT)/조회/수정/삭제
- **즉시 발송**: 캠페인을 즉시 발송 트리거 (ADMIN 권한 필요)
- **예약 발송**: 지정 시각에 발송되도록 예약
- **발송 취소**: 예약된 발송을 취소
- **상태 관리**: DRAFT → SCHEDULED/SENDING → SENT/CANCELLED 상태 전이

## 담당 테이블

`campaigns`

## API 경로

- `/api/projects/{projectId}/campaigns/**` — 캠페인 관리 (JWT 인증)

## 의존 관계

- `butterfly-common`, `butterfly-auth`, `butterfly-segment`에 의존
- Spring Data JPA, MySQL
