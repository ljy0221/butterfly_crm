package com.b101.butterfly;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * butterfly-api 컨텍스트 로드 검증.
 *
 * <p>P2(@EntityScan 세분화) 진행 중 임시 조치: ButterflyCampaignApplication이 자기 모듈
 * 내부에 광역 {@code @EnableJpaRepositories("com.b101.butterfly")}를 가지고 있어,
 * 본 통합 테스트에서 광역 component scan이 그걸 발견해 도메인 모듈의 AutoConfig
 * (예: AuthJpaAutoConfig)와 동일 repository 빈을 이중 등록한다. 임시로
 * {@code spring.main.allow-bean-definition-overriding=true}로 우회한다.
 *
 * <p>역할 4의 P2 작업(campaign 광역 어노테이션 제거)이 머지되면 이 properties는 제거할 수 있다.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class ButterflyApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
