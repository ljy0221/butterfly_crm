package com.b101.butterfly.reporting;

import com.b101.butterfly.reporting.reconciliation.port.StubCampaignExecutionPortConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

/**
 * Reporting 모듈 슬라이스 테스트용 Spring Boot 진입점.
 *
 * <p>{@code @DataJpaTest}는 컨텍스트 캐시를 공유할 수 있어, 서로 다른 도메인의 첫 테스트 컨텍스트가
 * 다른 도메인의 엔티티를 못 보는 캐시 누락이 발생할 수 있다. {@link EntityScan}으로 reporting
 * 패키지 전체의 엔티티가 일관되게 잡히도록 한다. JPA 리포지토리 스캔은 {@code ReportingJpaAutoConfig}
 * 가 담당하므로 여기서는 다시 선언하지 않는다 (중복 선언 시 BeanDefinitionOverrideException).
 *
 * <p>{@link StubCampaignExecutionPortConfig} 는 본 PR(S14P31B101-126 / 126-A)에서 도입된
 * SPI의 테스트용 stub 빈을 제공한다 — 실제 어댑터는 126-B에서 외부 팀 합의 후 구현된다.
 */
@SpringBootApplication
@EntityScan("com.b101.butterfly.reporting")
@Import(StubCampaignExecutionPortConfig.class)
public class ReportingTestApplication {
}
