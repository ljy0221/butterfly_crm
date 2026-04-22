# butterfly-api

## 역할

모든 도메인 모듈을 조립하여 하나의 REST API 서버로 구동하는 애플리케이션 모듈.

## 주요 기능

- **모듈 조립**: 모든 도메인 모듈을 의존성으로 포함하여 단일 실행 가능한 bootJar 생성
- **애플리케이션 진입점**: `ButterflyApiApplication` (`@SpringBootApplication`)
- **Actuator**: 헬스체크, 메트릭 등 운영 엔드포인트 제공
- **DevTools**: 개발 환경에서 자동 재시작 지원

## 의존 관계

- 모든 도메인 모듈에 의존 (common, auth, customer, segment, campaign, delivery, analytics)
- Spring Boot Starter Web, Actuator
