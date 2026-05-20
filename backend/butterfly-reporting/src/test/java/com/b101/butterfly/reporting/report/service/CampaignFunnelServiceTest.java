package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository.CampaignFunnelRawMetricRow;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelQuery;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelResponse;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelStageRow;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import com.b101.butterfly.reporting.report.dto.FunnelStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("CampaignFunnelService — 캠페인 단계 펀널")
class CampaignFunnelServiceTest {

    private CampaignStatsDailyRepository repo;
    private ReportingNotificationLogRepository notificationLogRepo;
    private CampaignFunnelService service;

    @BeforeEach
    void setUp() {
        repo = mock(CampaignStatsDailyRepository.class);
        notificationLogRepo = mock(ReportingNotificationLogRepository.class);
        service = new CampaignFunnelService(repo, notificationLogRepo);
    }

    private CampaignFunnelQuery query() {
        return new CampaignFunnelQuery(1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                null, null);
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("from이 to보다 뒤면 INVALID_DATE_RANGE")
        void rejectsInverted() {
            CampaignFunnelQuery q = new CampaignFunnelQuery(1L,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null, null);
            assertThatThrownBy(() -> service.getFunnel(q))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AnalyticsErrorCode.INVALID_DATE_RANGE);
        }

        @Test
        @DisplayName("기간 367일 초과면 DATE_RANGE_TOO_LARGE")
        void rejectsTooLarge() {
            CampaignFunnelQuery q = new CampaignFunnelQuery(1L,
                    LocalDate.of(2025, 1, 1), LocalDate.of(2026, 5, 1), null, null);
            assertThatThrownBy(() -> service.getFunnel(q))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AnalyticsErrorCode.DATE_RANGE_TOO_LARGE);
        }
    }

    @Nested
    @DisplayName("getFunnel")
    class GetFunnel {

        @Test
        @DisplayName("정상 — 5단계 모두 반환되며 비율 계산이 KPI 정의서와 일치")
        void normalFunnel() {
            given(repo.aggregateFunnel(eq(1L), any(), any(), any(), any()))
                    .willReturn(new FunnelCountRow(1000, 800, 200, 400, 100, 25));

            CampaignFunnelResponse res = service.getFunnel(query());

            assertThat(res.stages()).hasSize(5);
            assertThat(res.stages()).extracting(CampaignFunnelStageRow::stage)
                    .containsExactly(FunnelStage.SENT, FunnelStage.DELIVERED, FunnelStage.OPEN,
                            FunnelStage.CLICK, FunnelStage.CONVERT);

            CampaignFunnelStageRow sent = res.stages().get(0);
            assertThat(sent.count()).isEqualTo(1000);
            // SENT는 시작 단계 — 자기 자신 대비 100%
            assertThat(sent.conversionFromStart()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(sent.conversionFromPrev()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(sent.dropOffFromPrev()).isEqualByComparingTo(BigDecimal.ZERO);

            // DELIVERED: 800/1000 = 80%
            CampaignFunnelStageRow delivered = res.stages().get(1);
            assertThat(delivered.conversionFromStart()).isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(delivered.conversionFromPrev()).isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(delivered.dropOffFromPrev()).isEqualByComparingTo(new BigDecimal("20.00"));

            // OPEN: 400/1000 = 40% (start), 400/800 = 50% (prev=delivered)
            CampaignFunnelStageRow open = res.stages().get(2);
            assertThat(open.conversionFromStart()).isEqualByComparingTo(new BigDecimal("40.00"));
            assertThat(open.conversionFromPrev()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(open.dropOffFromPrev()).isEqualByComparingTo(new BigDecimal("50.00"));

            // CLICK: 100/1000 = 10%, 100/400 = 25%
            CampaignFunnelStageRow click = res.stages().get(3);
            assertThat(click.conversionFromStart()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(click.conversionFromPrev()).isEqualByComparingTo(new BigDecimal("25.00"));
            assertThat(click.dropOffFromPrev()).isEqualByComparingTo(new BigDecimal("75.00"));

            // CONVERT: 25/1000 = 2.5%, 25/100 = 25%
            CampaignFunnelStageRow convert = res.stages().get(4);
            assertThat(convert.conversionFromStart()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(convert.conversionFromPrev()).isEqualByComparingTo(new BigDecimal("25.00"));
            assertThat(convert.dropOffFromPrev()).isEqualByComparingTo(new BigDecimal("75.00"));
        }

        @Test
        @DisplayName("biggestDropOffStage — 직전 단계 대비 이탈률이 가장 큰 단계")
        void biggestDropOffPicksMaxRate() {
            // OPEN→CLICK 사이 75% 이탈, CLICK→CONVERT 사이 75% 이탈 (동률)
            // 첫 발견 단계인 CLICK이 선택되어야 함 (>이지 >=가 아님)
            given(repo.aggregateFunnel(any(), any(), any(), any(), any()))
                    .willReturn(new FunnelCountRow(1000, 800, 200, 400, 100, 25));
            CampaignFunnelResponse res = service.getFunnel(query());
            assertThat(res.biggestDropOffStage()).isEqualTo(FunnelStage.CLICK);
        }

        @Test
        @DisplayName("데이터 0건 — 모든 단계 count=0, 비율 모두 null, biggestDropOff null")
        void zeroData() {
            given(repo.aggregateFunnel(any(), any(), any(), any(), any()))
                    .willReturn(FunnelCountRow.zero());

            CampaignFunnelResponse res = service.getFunnel(query());

            assertThat(res.stages()).hasSize(5);
            assertThat(res.stages()).allSatisfy(row -> {
                assertThat(row.count()).isZero();
                assertThat(row.conversionFromStart()).isNull();
                assertThat(row.conversionFromPrev()).isNull();
                assertThat(row.dropOffFromPrev()).isNull();
            });
            assertThat(res.biggestDropOffStage()).isNull();
        }

        @Test
        @DisplayName("repository null 응답 — zero로 fallback")
        void repositoryReturnsNull() {
            given(repo.aggregateFunnel(any(), any(), any(), any(), any())).willReturn(null);

            CampaignFunnelResponse res = service.getFunnel(query());

            assertThat(res.stages()).hasSize(5);
            assertThat(res.stages().get(0).count()).isZero();
        }

        @Test
        @DisplayName("DELIVERED는 0이지만 OPEN은 양수 — DELIVERED 분모 0인 OPEN의 fromPrev는 null")
        void zeroDenominatorMidStage() {
            // 비현실적 데이터지만 분모 0 방어 검증용
            given(repo.aggregateFunnel(any(), any(), any(), any(), any()))
                    .willReturn(new FunnelCountRow(100, 0, 100, 50, 10, 1));

            CampaignFunnelResponse res = service.getFunnel(query());

            CampaignFunnelStageRow open = res.stages().get(2);
            // delivered=0이므로 OPEN/delivered = null
            assertThat(open.conversionFromPrev()).isNull();
            assertThat(open.dropOffFromPrev()).isNull();
            // 시작 단계 대비는 sent=100 분모로 정상 계산
            assertThat(open.conversionFromStart()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("필터 — campaignId/channel 파라미터가 그대로 응답에 반영")
        void filtersAreReflectedInResponse() {
            given(repo.aggregateFunnel(eq(1L), any(), any(), eq(100L), eq(Channel.PUSH)))
                    .willReturn(new FunnelCountRow(10, 8, 2, 4, 1, 0));

            CampaignFunnelQuery q = new CampaignFunnelQuery(1L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                    100L, Channel.PUSH);
            CampaignFunnelResponse res = service.getFunnel(q);

            assertThat(res.campaignId()).isEqualTo(100L);
            assertThat(res.channel()).isEqualTo(Channel.PUSH);
        }

        @Test
        @DisplayName("오늘이 포함된 기간 — daily 집계에 오늘 raw 발송/클릭을 더한다")
        void includesTodayRawFunnel() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            LocalDate yesterday = today.minusDays(1);
            given(repo.aggregateFunnel(eq(1L), eq(yesterday), eq(yesterday), any(), any()))
                    .willReturn(new FunnelCountRow(10, 9, 1, 4, 2, 1));
            given(notificationLogRepo.aggregateCampaignFunnelBetween(
                    eq(1L),
                    eq(today.atStartOfDay()),
                    eq(today.plusDays(1).atStartOfDay()),
                    any()
            )).willReturn(List.of(rawMetric(567L, "PUSH", 389L, 7L, 382L, 0L, 3L, 0L)));

            CampaignFunnelResponse res = service.getFunnel(new CampaignFunnelQuery(
                    1L, yesterday, today, null, null));

            assertThat(res.stages()).extracting(CampaignFunnelStageRow::count)
                    .containsExactly(399L, 16L, 4L, 5L, 1L);
        }
    }

    private static CampaignFunnelRawMetricRow rawMetric(
            Long campaignId,
            String channel,
            Long sent,
            Long delivered,
            Long failed,
            Long open,
            Long click,
            Long convert
    ) {
        return new CampaignFunnelRawMetricRow() {
            @Override
            public Long getCampaignId() {
                return campaignId;
            }

            @Override
            public String getChannel() {
                return channel;
            }

            @Override
            public Long getSentCount() {
                return sent;
            }

            @Override
            public Long getDeliveredCount() {
                return delivered;
            }

            @Override
            public Long getFailedCount() {
                return failed;
            }

            @Override
            public Long getOpenCount() {
                return open;
            }

            @Override
            public Long getClickCount() {
                return click;
            }

            @Override
            public Long getConvertCount() {
                return convert;
            }
        };
    }
}
