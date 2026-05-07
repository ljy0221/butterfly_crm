package com.b101.butterfly.reporting.service;

import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("AnalyticsServiceImpl — getCampaignStats 누적 통계 (S14P31B101-34)")
class AnalyticsServiceImplTest {

    private CampaignStatsDailyRepository repository;
    private AnalyticsServiceStub stub;
    private AnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignStatsDailyRepository.class);
        stub = mock(AnalyticsServiceStub.class);
        service = new AnalyticsServiceImpl(repository, stub);
    }

    @Test
    @DisplayName("정상 — 카운트 + KPI 정의서 비율 (open/delivered, click/open, convert/sent)")
    void normalAggregation() {
        // sent=1000, delivered=800, open=400, click=100, convert=25
        given(repository.aggregateFunnel(eq(1L), any(), any(), eq(100L), any()))
                .willReturn(new FunnelCountRow(1000, 800, 400, 100, 25));

        CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

        assertThat(res.campaignId()).isEqualTo(100L);
        assertThat(res.sentCount()).isEqualTo(1000);
        assertThat(res.deliveredCount()).isEqualTo(800);
        assertThat(res.openCount()).isEqualTo(400);
        assertThat(res.clickedCount()).isEqualTo(100);
        assertThat(res.convertCount()).isEqualTo(25);
        // failed = sent - delivered = 200
        assertThat(res.failedCount()).isEqualTo(200);

        // 비율 (ADR-0007): %, 소수점 2자리
        assertThat(res.deliveryRate()).isEqualTo(80.0);   // 800/1000
        assertThat(res.openRate()).isEqualTo(50.0);       // 400/800
        assertThat(res.clickRate()).isEqualTo(25.0);      // 100/400
        assertThat(res.convertRate()).isEqualTo(2.5);     // 25/1000

        assertThat(res.executions()).isEmpty();
        assertThat(res.metricsComputedAt()).isNotNull();
    }

    @Test
    @DisplayName("repository null — FunnelCountRow.zero로 fallback")
    void repositoryReturnsNull() {
        given(repository.aggregateFunnel(any(), any(), any(), any(), any())).willReturn(null);

        CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

        assertThat(res.sentCount()).isZero();
        // 분모 0 → 모든 비율 null
        assertThat(res.deliveryRate()).isNull();
        assertThat(res.openRate()).isNull();
        assertThat(res.clickRate()).isNull();
        assertThat(res.convertRate()).isNull();
    }

    @Test
    @DisplayName("데이터 0건 — zero row 반환되며 비율은 모두 null")
    void zeroDataAllRatesNull() {
        given(repository.aggregateFunnel(any(), any(), any(), any(), any()))
                .willReturn(FunnelCountRow.zero());

        CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

        assertThat(res.sentCount()).isZero();
        assertThat(res.deliveryRate()).isNull();
        assertThat(res.openRate()).isNull();
        assertThat(res.clickRate()).isNull();
        assertThat(res.convertRate()).isNull();
        assertThat(res.failedCount()).isZero();
    }

    @Test
    @DisplayName("delivered=0 인데 open>0 — KPI 정의서: openRate null (분모 0)")
    void zeroDeliveredMakesOpenRateNull() {
        // 비현실적 데이터지만 분모 0 방어 검증
        given(repository.aggregateFunnel(any(), any(), any(), any(), any()))
                .willReturn(new FunnelCountRow(100, 0, 50, 10, 1));

        CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

        // delivered=0 ⇒ openRate null
        assertThat(res.openRate()).isNull();
        // sent=100 분모는 정상 — convertRate 계산됨
        assertThat(res.convertRate()).isEqualTo(1.0);  // 1/100
        // open=50 분모로 clickRate 계산됨
        assertThat(res.clickRate()).isEqualTo(20.0);   // 10/50
    }

    @Test
    @DisplayName("ADR-0007 분모 변경 검증 — clickRate는 click/open (delivered 아님)")
    void clickRateUsesOpenAsDenominator() {
        // delivered=1000, open=200, click=100
        // 만약 click/delivered (구 ADR-0003) → 10%
        // ADR-0007: click/open → 50%
        given(repository.aggregateFunnel(any(), any(), any(), any(), any()))
                .willReturn(new FunnelCountRow(2000, 1000, 200, 100, 50));

        CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

        assertThat(res.clickRate()).isEqualTo(50.0);  // click/open = 100/200
    }

    @Test
    @DisplayName("aggregateFunnel 호출 시 channel=null로 전달 (모든 채널 합산)")
    void callsRepositoryWithoutChannelFilter() {
        given(repository.aggregateFunnel(any(), any(), any(), any(), any()))
                .willReturn(FunnelCountRow.zero());

        service.getCampaignStats(1L, 100L);

        // ArgumentCaptor 대신 verify 패턴 단순화 — eq로 호출 확인
        org.mockito.Mockito.verify(repository).aggregateFunnel(
                eq(1L),
                eq(LocalDate.of(1970, 1, 1)),
                eq(LocalDate.of(9999, 12, 31)),
                eq(100L),
                eq(null)  // 모든 채널 합산
        );
    }
}
