package com.b101.butterfly.reporting.reconciliation.port;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 테스트 컨텍스트에서 {@link CampaignExecutionPort} 의 기본 stub 빈을 제공한다.
 *
 * <p>실제 어댑터는 S14P31B101-187 (126-B) 에서 외부 팀 합의 후 구현되므로, 그 전까지 본 PR이
 * 추가한 ReconciliationController/Service가 다른 모듈의 `@SpringBootTest` 전체 컨텍스트
 * 부팅을 깨뜨리지 않도록 빈 응답을 주는 stub을 둔다.
 *
 * <p>Service 단위 테스트는 Mockito mock을 직접 주입하므로 본 stub의 영향을 받지 않는다.
 *
 * <p>{@link TestComponent}로 표시되어 production 컨텍스트(`butterfly-api`/`butterfly-worker`)
 * 에는 영향을 주지 않는다.
 */
@TestComponent
public class StubCampaignExecutionPortConfig {

    @Bean
    public CampaignExecutionPort defaultStubCampaignExecutionPort() {
        return new CampaignExecutionPort() {
            @Override
            public Optional<CampaignExecutionInfo> find(Long executionId) {
                return Optional.empty();
            }

            @Override
            public List<CampaignExecutionInfo> findByCampaign(Long campaignId) {
                return List.of();
            }

            @Override
            public List<CampaignExecutionInfo> findRecent(LocalDateTime since) {
                return List.of();
            }
        };
    }
}
