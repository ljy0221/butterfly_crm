package com.b101.butterfly.reporting.service;

import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.dto.AnalyticsQuery;
import com.b101.butterfly.reporting.dto.CampaignMetricItem;
import com.b101.butterfly.reporting.dto.CampaignMetricRow;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.dto.ChannelMetricRow;
import com.b101.butterfly.reporting.dto.ChannelMetricsResponse;
import com.b101.butterfly.reporting.dto.DailyTrendRow;
import com.b101.butterfly.reporting.dto.DashboardResponse;
import com.b101.butterfly.reporting.dto.PeriodSummaryRow;
import com.b101.butterfly.reporting.dto.SegmentMetricsResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository.CampaignFunnelRawRow;
import com.b101.butterfly.reporting.report.dto.CampaignChannelStatsRow;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import com.b101.butterfly.reporting.report.port.CampaignSegmentInfo;
import com.b101.butterfly.reporting.report.port.CampaignSegmentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("AnalyticsServiceImpl — 4개 분석 API 실 구현 (S14P31B101-202)")
class AnalyticsServiceImplTest {

    private CampaignStatsDailyRepository campaignRepo;
    private ProjectStatsDailyRepository projectRepo;
    private CampaignSegmentPort segmentPort;
    private ReportingNotificationLogRepository notificationLogRepo;
    private AnalyticsServiceStub stub;
    private AnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        campaignRepo = mock(CampaignStatsDailyRepository.class);
        projectRepo = mock(ProjectStatsDailyRepository.class);
        segmentPort = mock(CampaignSegmentPort.class);
        notificationLogRepo = mock(ReportingNotificationLogRepository.class);
        stub = mock(AnalyticsServiceStub.class);
        service = new AnalyticsServiceImpl(campaignRepo, projectRepo, segmentPort, notificationLogRepo, stub);
    }

    /** {@link CampaignFunnelRawRow} 는 interface projection — anonymous impl 로 테스트값 주입. */
    private static CampaignFunnelRawRow rawRow(Long sent, Long delivered, Long failed) {
        return rawRow(sent, delivered, failed, 0L, 0L, 0L);
    }

    private static CampaignFunnelRawRow rawRow(Long sent, Long delivered, Long failed,
                                               Long open, Long click, Long convert) {
        return new CampaignFunnelRawRow() {
            @Override public Long getSentCount() { return sent; }
            @Override public Long getDeliveredCount() { return delivered; }
            @Override public Long getFailedCount() { return failed; }
            @Override public Long getOpenCount() { return open; }
            @Override public Long getClickCount() { return click; }
            @Override public Long getConvertCount() { return convert; }
        };
    }

    private AnalyticsQuery query() {
        return new AnalyticsQuery(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, null);
    }

    // ========== API 58 — getCampaignStats ==========

    @Nested
    @DisplayName("getCampaignStats (API 58)")
    class CampaignStatsTest {

        @Test
        @DisplayName("정상 — daily 우선 조회 시 fallback 미호출, KPI 정의서 비율")
        void normalAggregation() {
            given(campaignRepo.aggregateFunnel(eq(1L), any(), any(), eq(100L), any()))
                    .willReturn(new FunnelCountRow(1000, 800, 200, 400, 100, 25));

            CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

            assertThat(res.campaignId()).isEqualTo(100L);
            assertThat(res.sentCount()).isEqualTo(1000);
            assertThat(res.failedCount()).isEqualTo(200);
            assertThat(res.deliveryRate()).isEqualTo(80.0);
            assertThat(res.openRate()).isEqualTo(50.0);     // ADR-0007: open/delivered
            assertThat(res.clickRate()).isEqualTo(25.0);    // ADR-0007: click/open
            assertThat(res.convertRate()).isEqualTo(2.5);
            // daily 히트 시 fallback 호출 금지
            org.mockito.Mockito.verifyNoInteractions(notificationLogRepo);
        }

        @Test
        @DisplayName("daily null — raw notification_logs fallback 호출 + 카운트 반영")
        void dailyNullFallsBackToRawLogs() {
            given(campaignRepo.aggregateFunnel(any(), any(), any(), any(), any())).willReturn(null);
            given(notificationLogRepo.countCampaignFunnel(1L, 100L))
                    .willReturn(rawRow(120L, 100L, 20L));

            CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

            assertThat(res.sentCount()).isEqualTo(120L);
            assertThat(res.deliveredCount()).isEqualTo(100L);
            assertThat(res.failedCount()).isEqualTo(20L);
            assertThat(res.deliveryRate()).isEqualTo(83.33);
            assertThat(res.openCount()).isZero();
            // ADR-0007: 분모(delivered=100)가 0이 아니므로 openRate=0.0 (rate(0,100)=0.0).
            // 분자가 0이어도 null이 아님 — null 은 "정의 불가(분모 0)" 시만.
            assertThat(res.openRate()).isEqualTo(0.0);
            assertThat(res.clickRate()).isNull();   // 분모(open=0) → null
            assertThat(res.convertRate()).isEqualTo(0.0); // 분모(sent=120) ≠ 0
        }

        @Test
        @DisplayName("daily null — raw fallback에서 user_action_logs 카운트도 반영")
        void rawFallbackIncludesActionCounts() {
            given(campaignRepo.aggregateFunnel(any(), any(), any(), any(), any())).willReturn(null);
            given(notificationLogRepo.countCampaignFunnel(1L, 100L))
                    .willReturn(rawRow(120L, 100L, 20L, 80L, 30L, 10L));

            CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

            assertThat(res.openCount()).isEqualTo(80L);
            assertThat(res.clickedCount()).isEqualTo(30L);
            assertThat(res.convertCount()).isEqualTo(10L);
            assertThat(res.openRate()).isEqualTo(80.0);
            assertThat(res.clickRate()).isEqualTo(37.5);
            assertThat(res.convertRate()).isEqualTo(8.33);
        }

        @Test
        @DisplayName("daily sent=0 — fallback 시도, raw도 0이면 모든 비율 null")
        void dailyEmptyAndRawEmpty() {
            given(campaignRepo.aggregateFunnel(any(), any(), any(), any(), any()))
                    .willReturn(new FunnelCountRow(0, 0, 0, 0, 0, 0));
            given(notificationLogRepo.countCampaignFunnel(1L, 100L))
                    .willReturn(rawRow(0L, 0L, 0L));

            CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

            assertThat(res.sentCount()).isZero();
            assertThat(res.deliveryRate()).isNull();
        }

        @Test
        @DisplayName("daily null + raw도 null — 0 응답, 비율 null (DB 미초기화/오류 안전망)")
        void dailyAndRawBothNull() {
            given(campaignRepo.aggregateFunnel(any(), any(), any(), any(), any())).willReturn(null);
            given(notificationLogRepo.countCampaignFunnel(1L, 100L)).willReturn(null);

            CampaignStatsResponse res = service.getCampaignStats(1L, 100L);

            assertThat(res.sentCount()).isZero();
            assertThat(res.deliveryRate()).isNull();
        }
    }

    // ========== API 57 — getDashboard ==========

    @Nested
    @DisplayName("getDashboard (API 57)")
    class DashboardTest {

        @Test
        @DisplayName("정상 — Summary + DailyTrend 시계열")
        void normalDashboard() {
            given(projectRepo.aggregatePeriod(eq(1L), any(), any()))
                    .willReturn(new PeriodSummaryRow(1000, 800, 400, 100, 25, 200, 5));
            given(projectRepo.findDailyTrend(eq(1L), any(), any())).willReturn(List.of(
                    new DailyTrendRow(LocalDate.of(2026, 4, 1), 500, 400, 200, 50, 12),
                    new DailyTrendRow(LocalDate.of(2026, 4, 2), 500, 400, 200, 50, 13)
            ));

            DashboardResponse res = service.getDashboard(1L, query());

            assertThat(res.summary().campaignsSentInPeriod()).isEqualTo(5L);
            assertThat(res.summary().totalSent()).isEqualTo(1000L);
            assertThat(res.summary().avgDeliveryRate()).isEqualTo(80.0);
            assertThat(res.summary().avgOpenRate()).isEqualTo(50.0);
            assertThat(res.summary().avgClickRate()).isEqualTo(25.0);

            assertThat(res.dailyTrend()).hasSize(2);
            assertThat(res.dailyTrend().get(0).date()).isEqualTo(LocalDate.of(2026, 4, 1));
        }

        @Test
        @DisplayName("기간 데이터 없음 — Summary 0 + 빈 trend")
        void emptyPeriod() {
            given(projectRepo.aggregatePeriod(any(), any(), any())).willReturn(null);
            given(projectRepo.findDailyTrend(any(), any(), any())).willReturn(List.of());

            DashboardResponse res = service.getDashboard(1L, query());

            assertThat(res.summary().totalSent()).isZero();
            assertThat(res.summary().avgDeliveryRate()).isNull();
            assertThat(res.dailyTrend()).isEmpty();
        }
    }

    // ========== API 60 — getChannelMetrics ==========

    @Nested
    @DisplayName("getChannelMetrics (API 60)")
    class ChannelMetricsTest {

        @Test
        @DisplayName("정상 — 채널별 카운트 + 비율")
        void normalChannelMetrics() {
            given(campaignRepo.aggregateByChannel(eq(1L), any(), any())).willReturn(List.of(
                    new ChannelMetricRow(Channel.PUSH, 1000, 800, 400, 100, 25, 200),
                    new ChannelMetricRow(Channel.EMAIL, 500, 450, 100, 20, 5, 50)
            ));

            ChannelMetricsResponse res = service.getChannelMetrics(1L, query());

            assertThat(res.channels()).hasSize(2);
            assertThat(res.channels().get(0).channel()).isEqualTo("PUSH");
            assertThat(res.channels().get(0).deliveryRate()).isEqualTo(80.0);
            assertThat(res.channels().get(0).openRate()).isEqualTo(50.0);
            assertThat(res.channels().get(1).channel()).isEqualTo("EMAIL");
        }

        @Test
        @DisplayName("데이터 없음 — 빈 리스트")
        void emptyChannels() {
            given(campaignRepo.aggregateByChannel(any(), any(), any())).willReturn(List.of());
            ChannelMetricsResponse res = service.getChannelMetrics(1L, query());
            assertThat(res.channels()).isEmpty();
        }
    }

    // ========== API 59 — getCampaignMetrics ==========

    @Nested
    @DisplayName("getCampaignMetrics (API 59)")
    class CampaignMetricsTest {

        @Test
        @DisplayName("정상 — 페이지네이션 + 비율")
        void normalCampaignMetrics() {
            given(campaignRepo.countCampaignChannelPairs(any(), any(), any(), any(), any()))
                    .willReturn(2L);
            given(campaignRepo.aggregateByCampaign(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .willReturn(List.of(
                            new CampaignMetricRow(100L, "PUSH", 1000, 800, 400, 100, 25, 200),
                            new CampaignMetricRow(101L, "EMAIL", 500, 450, 100, 20, 5, 50)
                    ));

            PageResponse<CampaignMetricItem> res = service.getCampaignMetrics(1L, query(), 0, 10);

            assertThat(res.totalElements()).isEqualTo(2L);
            assertThat(res.content()).hasSize(2);
            assertThat(res.content().get(0).campaignId()).isEqualTo(100L);
            assertThat(res.content().get(0).campaignName()).isEqualTo("campaign-100");  // placeholder
            assertThat(res.content().get(0).deliveryRate()).isEqualTo(80.0);
        }

        @Test
        @DisplayName("segment 필터 — segmentMap에서 매칭되는 캠페인이 없으면 즉시 빈 페이지")
        void segmentFilterNoMatch() {
            AnalyticsQuery q = new AnalyticsQuery(
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, 999L);

            given(campaignRepo.aggregateByProjectAndDateRange(eq(1L), any(), any()))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(100L, Channel.PUSH, 100, 80, 5, 40, 10, 5)));
            given(segmentPort.findByCampaignIds(anyList())).willReturn(Map.of(
                    100L, new CampaignSegmentInfo(100L, 10L, "VIP")  // segment=10, 필터=999 → 매칭 0
            ));

            PageResponse<CampaignMetricItem> res = service.getCampaignMetrics(1L, q, 0, 10);

            assertThat(res.content()).isEmpty();
            assertThat(res.totalElements()).isZero();
        }

        @Test
        @DisplayName("segment 필터 — 매칭 캠페인을 campaignIdsFilter로 전달")
        void segmentFilterApplied() {
            AnalyticsQuery q = new AnalyticsQuery(
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, 10L);

            given(campaignRepo.aggregateByProjectAndDateRange(eq(1L), any(), any()))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(100L, Channel.PUSH, 100, 80, 5, 40, 10, 5)));
            given(segmentPort.findByCampaignIds(anyList())).willReturn(Map.of(
                    100L, new CampaignSegmentInfo(100L, 10L, "VIP")  // segment 10 매칭
            ));
            given(campaignRepo.countCampaignChannelPairs(any(), any(), any(), any(), any()))
                    .willReturn(1L);
            given(campaignRepo.aggregateByCampaign(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .willReturn(List.of(
                            new CampaignMetricRow(100L, "PUSH", 100, 80, 40, 10, 5, 20)));

            PageResponse<CampaignMetricItem> res = service.getCampaignMetrics(1L, q, 0, 10);

            assertThat(res.content()).hasSize(1);
            assertThat(res.content().get(0).campaignId()).isEqualTo(100L);
        }
    }

    // ========== API 61 — getSegmentMetrics ==========

    @Nested
    @DisplayName("getSegmentMetrics (API 61)")
    class SegmentMetricsTest {

        @Test
        @DisplayName("정상 — segment 단위 합산 + 캠페인 distinct count")
        void normalSegmentMetrics() {
            // 캠페인 100/101은 segment 10, campaign 102는 segment 20
            given(campaignRepo.aggregateByProjectAndDateRange(eq(1L), any(), any()))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(100L, Channel.PUSH, 1000, 800, 0, 400, 100, 25),
                            new CampaignChannelStatsRow(101L, Channel.EMAIL, 500, 450, 0, 100, 20, 5),
                            new CampaignChannelStatsRow(102L, Channel.PUSH, 200, 200, 0, 50, 10, 1)
                    ));
            given(segmentPort.findByCampaignIds(anyList())).willReturn(Map.of(
                    100L, new CampaignSegmentInfo(100L, 10L, "VIP"),
                    101L, new CampaignSegmentInfo(101L, 10L, "VIP"),
                    102L, new CampaignSegmentInfo(102L, 20L, "NEW")
            ));

            SegmentMetricsResponse res = service.getSegmentMetrics(1L, query());

            assertThat(res.segments()).hasSize(2);
            // sentCount DESC 정렬 — VIP 1500 > NEW 200
            assertThat(res.segments().get(0).segmentId()).isEqualTo(10L);
            assertThat(res.segments().get(0).segmentName()).isEqualTo("VIP");
            assertThat(res.segments().get(0).campaignCount()).isEqualTo(2L);
            assertThat(res.segments().get(0).sentCount()).isEqualTo(1500L);     // 1000+500
            assertThat(res.segments().get(0).deliveredCount()).isEqualTo(1250L); // 800+450
            assertThat(res.segments().get(0).openCount()).isEqualTo(500L);      // 400+100
            assertThat(res.segments().get(0).deliveryRate()).isEqualTo(83.33);   // 1250/1500

            assertThat(res.segments().get(1).segmentId()).isEqualTo(20L);
            assertThat(res.segments().get(1).campaignCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("미지정 캠페인 — segmentId=null 그룹으로 별도 row")
        void unsegmentedCampaignGroup() {
            given(campaignRepo.aggregateByProjectAndDateRange(eq(1L), any(), any()))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(100L, Channel.PUSH, 100, 80, 0, 40, 10, 5)));
            given(segmentPort.findByCampaignIds(anyList())).willReturn(Map.of(
                    100L, new CampaignSegmentInfo(100L, null, null)
            ));

            SegmentMetricsResponse res = service.getSegmentMetrics(1L, query());

            assertThat(res.segments()).hasSize(1);
            assertThat(res.segments().get(0).segmentId()).isNull();
            assertThat(res.segments().get(0).segmentName()).isNull();
        }

        @Test
        @DisplayName("데이터 없음 — 빈 리스트, segmentPort 호출 없음")
        void emptyData() {
            given(campaignRepo.aggregateByProjectAndDateRange(any(), any(), any())).willReturn(List.of());

            SegmentMetricsResponse res = service.getSegmentMetrics(1L, query());

            assertThat(res.segments()).isEmpty();
            org.mockito.Mockito.verifyNoInteractions(segmentPort);
        }
    }

    // ========== execution stats — stub 위임 ==========

    @Test
    @DisplayName("getExecutionStats — stub에 위임 (campaign_executions SPI 후속)")
    void executionStatsDelegatesToStub() {
        service.getExecutionStats(1L, 100L, 9999L);
        org.mockito.Mockito.verify(stub).getExecutionStats(1L, 100L, 9999L);
    }
}
