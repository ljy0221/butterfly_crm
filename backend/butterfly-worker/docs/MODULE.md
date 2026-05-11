# butterfly-worker

## 역할

비동기 메시지를 소비하여 실제 발송 처리 및 이벤트 가공을 수행하는 워커 애플리케이션 모듈.

## 주요 기능

- **메시지 소비**: Kafka로부터 발송 요청, 이벤트 수집 등의 메시지를 소비
- **발송 실행**: 채널별(앱 푸시, 웹 푸시 등) 실제 발송 처리
- **이벤트 처리**: SDK에서 수집된 이벤트의 비동기 저장/가공
- **통계 집계**: 발송 결과를 기반으로 캠페인 통계 업데이트

## 의존 관계

- 모든 도메인 모듈에 의존 (common, auth, customer, segment, campaign, delivery, analytics)
- Spring Kafka, Spring Data Redis, Actuator
