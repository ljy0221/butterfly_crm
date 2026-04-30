package com.b101.butterfly.event;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Repository 슬라이스 테스트용 부트 진입점. butterfly-event는 라이브러리 모듈이라 운영 진입점이
 * 없으므로 {@code @DataJpaTest}가 패키지 상위로 올라가다 부트 설정을 못 찾아 테스트가 깨진다.
 * 테스트 src 트리에만 두는 최소 진입점.
 */
@SpringBootApplication
public class EventTestApplication {
}
