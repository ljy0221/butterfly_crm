# butterfly-notification

## 역할

실제 메시지 발송 처리 및 발송 로그 관리를 담당하는 모듈. 채널별(앱 푸시, 웹 푸시 등) 발송 로직을 구현한다.

## 주요 기능

- **발송 처리**: RabbitMQ/Kafka를 통해 전달된 발송 요청을 채널별로 처리
- **발송 로그 조회**: 캠페인별, 유저별 발송 이력 조회
- **발송 로그 상세**: 개별 발송 건의 상태 추적 (SENT → DELIVERED → CLICKED/FAILED)
- **웹훅/콜백**: 발송 결과 콜백 수신 및 상태 업데이트

## 담당 테이블

`notification_logs`

## API 경로

- `/api/projects/{projectId}/campaigns/{campaignId}/logs` — 캠페인 발송 로그
- `/api/projects/{projectId}/logs/{logId}` — 발송 로그 상세
- `/api/projects/{projectId}/users/{userId}/logs` — 유저별 발송 이력

## 의존 관계

- `butterfly-common`, `butterfly-auth`, `butterfly-event`, `butterfly-campaign`에 의존
- Spring Data JPA, Spring AMQP, Spring Kafka, Spring Data Redis, MySQL
