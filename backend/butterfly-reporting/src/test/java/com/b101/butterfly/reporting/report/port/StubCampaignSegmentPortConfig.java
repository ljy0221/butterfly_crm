package com.b101.butterfly.reporting.report.port;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * 테스트 컨텍스트에서 {@link CampaignSegmentPort}의 기본 stub 빈을 제공한다 (S14P31B101-92).
 *
 * <p>실제 어댑터는 butterfly-api 조립 레벨의 {@code CampaignSegmentPortAdapter}이므로, 다른
 * 도메인의 {@code @SpringBootTest} 전체 컨텍스트가 본 PR 도입 후에도 깨지지 않도록 빈 응답을
 * 주는 stub을 둔다.
 *
 * <p>{@link com.b101.butterfly.reporting.report.service.SegmentChannelMatrixService} 단위
 * 테스트는 Mockito mock을 직접 주입하므로 본 stub의 영향을 받지 않는다.
 *
 * <p>{@link TestComponent}로 표시되어 production 컨텍스트(butterfly-api/butterfly-worker)
 * 에는 영향을 주지 않는다.
 */
@TestComponent
public class StubCampaignSegmentPortConfig {

    @Bean
    public CampaignSegmentPort defaultStubCampaignSegmentPort() {
        return (List<Long> campaignIds) -> Map.of();
    }
}
