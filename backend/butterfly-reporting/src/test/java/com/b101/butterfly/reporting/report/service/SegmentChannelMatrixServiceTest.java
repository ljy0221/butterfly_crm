package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignChannelStatsRow;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixQuery;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixResponse;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixRow;
import com.b101.butterfly.reporting.report.port.CampaignSegmentInfo;
import com.b101.butterfly.reporting.report.port.CampaignSegmentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("SegmentChannelMatrixService — segment×channel 성과 매트릭스")
class SegmentChannelMatrixServiceTest {

    private CampaignStatsDailyRepository repo;
    private CampaignSegmentPort port;
    private SegmentChannelMatrixService service;

    @BeforeEach
    void setUp() {
        repo = mock(CampaignStatsDailyRepository.class);
        port = mock(CampaignSegmentPort.class);
        service = new SegmentChannelMatrixService(repo, port);
    }

    private SegmentChannelMatrixQuery query(SegmentChannelMatrixQuery.SortBy sortBy) {
        return new SegmentChannelMatrixQuery(
                1L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                null, null,
                sortBy, false
        );
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("from > to → INVALID_DATE_RANGE")
        void rejectsInvertedRange() {
            SegmentChannelMatrixQuery q = new SegmentChannelMatrixQuery(
                    1L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1),
                    null, null, null, false
            );
            assertThatThrownBy(() -> service.getMatrix(q))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AnalyticsErrorCode.INVALID_DATE_RANGE);
        }

        @Test
        @DisplayName("기간 367일 초과 → DATE_RANGE_TOO_LARGE")
        void rejectsTooLargeRange() {
            SegmentChannelMatrixQuery q = new SegmentChannelMatrixQuery(
                    1L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 5, 1),
                    null, null, null, false
            );
            assertThatThrownBy(() -> service.getMatrix(q))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AnalyticsErrorCode.DATE_RANGE_TOO_LARGE);
        }
    }

    @Nested
    @DisplayName("getMatrix")
    class GetMatrix {

        @Test
        @DisplayName("빈 결과 — 빈 rows + null highlights")
        void emptyResult() {
            given(repo.aggregateByProjectAndDateRange(1L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of());

            SegmentChannelMatrixResponse res = service.getMatrix(query(null));

            assertThat(res.rows()).isEmpty();
            assertThat(res.highlights().topByOpenRate()).isNull();
        }

        @Test
        @DisplayName("같은 segment의 여러 캠페인을 segment×channel로 재합산")
        void aggregatesByGroupKey() {
            // 캠페인 100/101 모두 segment 10에 속하고 PUSH 채널
            given(repo.aggregateByProjectAndDateRange(1L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(100L, Channel.PUSH,
                                    1000, 800, 10, 400, 100, 50),
                            new CampaignChannelStatsRow(101L, Channel.PUSH,
                                    500, 400, 5, 200, 50, 25)
                    ));
            given(port.findByCampaignIds(ArgumentMatchers.anyList()))
                    .willReturn(Map.of(
                            100L, new CampaignSegmentInfo(100L, 10L, "VIP"),
                            101L, new CampaignSegmentInfo(101L, 10L, "VIP")
                    ));

            SegmentChannelMatrixResponse res = service.getMatrix(query(null));

            assertThat(res.rows()).hasSize(1);
            SegmentChannelMatrixRow row = res.rows().get(0);
            assertThat(row.segmentId()).isEqualTo(10L);
            assertThat(row.segmentName()).isEqualTo("VIP");
            assertThat(row.sentCount()).isEqualTo(1500);
            assertThat(row.deliveredCount()).isEqualTo(1200);
            // delivery_rate = 1200/1500*100 = 80.00
            assertThat(row.deliveryRate()).isEqualByComparingTo(new BigDecimal("80.00"));
            // open_rate = 600/1200*100 = 50.00
            assertThat(row.openRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("세그먼트 미지정 캠페인은 별도 그룹(segmentId=null)")
        void unsegmentedCampaignsBecomeNullGroup() {
            given(repo.aggregateByProjectAndDateRange(1L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(200L, Channel.EMAIL,
                                    100, 90, 0, 50, 10, 5)
                    ));
            // adapter가 null 세그먼트로 매핑된 케이스
            given(port.findByCampaignIds(ArgumentMatchers.anyList()))
                    .willReturn(Map.of(
                            200L, new CampaignSegmentInfo(200L, null, null)
                    ));

            SegmentChannelMatrixResponse res = service.getMatrix(query(null));

            assertThat(res.rows()).hasSize(1);
            assertThat(res.rows().get(0).segmentId()).isNull();
            assertThat(res.rows().get(0).segmentName()).isNull();
            assertThat(res.rows().get(0).channel()).isEqualTo(Channel.EMAIL);
        }

        @Test
        @DisplayName("port에 매핑이 없는 campaignId도 null 그룹으로 흡수 (어댑터 결손 방어)")
        void missingPortMappingFallsToNullGroup() {
            given(repo.aggregateByProjectAndDateRange(1L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(999L, Channel.SMS,
                                    50, 50, 0, 25, 5, 2)
                    ));
            given(port.findByCampaignIds(ArgumentMatchers.anyList())).willReturn(Map.of());

            SegmentChannelMatrixResponse res = service.getMatrix(query(null));
            assertThat(res.rows()).hasSize(1);
            assertThat(res.rows().get(0).segmentId()).isNull();
        }

        @Test
        @DisplayName("분모 0인 비율 지표는 null")
        void zeroDenominatorRateIsNull() {
            given(repo.aggregateByProjectAndDateRange(1L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of(
                            new CampaignChannelStatsRow(300L, Channel.PUSH,
                                    0, 0, 0, 0, 0, 0)
                    ));
            given(port.findByCampaignIds(ArgumentMatchers.anyList()))
                    .willReturn(Map.of(300L, new CampaignSegmentInfo(300L, 1L, "Empty")));

            SegmentChannelMatrixRow row = service.getMatrix(query(null)).rows().get(0);
            assertThat(row.deliveryRate()).isNull();
            assertThat(row.openRate()).isNull();
            assertThat(row.clickRate()).isNull();
            assertThat(row.convertRate()).isNull();
        }
    }

    @Nested
    @DisplayName("필터 / 정렬 / highlights")
    class FilterSortHighlight {

        private void givenTwoSegmentsTwoChannels() {
            given(repo.aggregateByProjectAndDateRange(1L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of(
                            // VIP × PUSH: 높은 open rate
                            new CampaignChannelStatsRow(100L, Channel.PUSH,
                                    1000, 1000, 0, 800, 200, 100),
                            // VIP × EMAIL: 낮은 open rate
                            new CampaignChannelStatsRow(101L, Channel.EMAIL,
                                    1000, 1000, 0, 100, 20, 10),
                            // NEW × PUSH: 중간 open rate
                            new CampaignChannelStatsRow(102L, Channel.PUSH,
                                    500, 500, 0, 250, 50, 30)
                    ));
            given(port.findByCampaignIds(ArgumentMatchers.anyList()))
                    .willReturn(Map.of(
                            100L, new CampaignSegmentInfo(100L, 10L, "VIP"),
                            101L, new CampaignSegmentInfo(101L, 10L, "VIP"),
                            102L, new CampaignSegmentInfo(102L, 20L, "NEW")
                    ));
        }

        @Test
        @DisplayName("channel 필터 — PUSH만")
        void filterByChannel() {
            givenTwoSegmentsTwoChannels();
            SegmentChannelMatrixQuery q = new SegmentChannelMatrixQuery(
                    1L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                    Set.of(Channel.PUSH), null, null, false
            );
            SegmentChannelMatrixResponse res = service.getMatrix(q);

            assertThat(res.rows()).hasSize(2);
            assertThat(res.rows()).allSatisfy(r ->
                    assertThat(r.channel()).isEqualTo(Channel.PUSH));
        }

        @Test
        @DisplayName("segmentIds 필터 — VIP만")
        void filterBySegment() {
            givenTwoSegmentsTwoChannels();
            SegmentChannelMatrixQuery q = new SegmentChannelMatrixQuery(
                    1L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                    null, Set.of(10L), null, false
            );
            SegmentChannelMatrixResponse res = service.getMatrix(q);

            assertThat(res.rows()).hasSize(2);
            assertThat(res.rows()).allSatisfy(r ->
                    assertThat(r.segmentName()).isEqualTo("VIP"));
        }

        @Test
        @DisplayName("정렬 — OPEN_RATE 내림차순 (default)")
        void sortByOpenRateDesc() {
            givenTwoSegmentsTwoChannels();
            SegmentChannelMatrixQuery q = query(SegmentChannelMatrixQuery.SortBy.OPEN_RATE);
            // ascending=false (default)
            SegmentChannelMatrixResponse res = service.getMatrix(q);

            // VIP×PUSH(80%), NEW×PUSH(50%), VIP×EMAIL(10%)
            assertThat(res.rows()).extracting(SegmentChannelMatrixRow::openRate)
                    .containsExactly(
                            new BigDecimal("80.00"),
                            new BigDecimal("50.00"),
                            new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("highlights — top open rate = VIP×PUSH, bottom = VIP×EMAIL")
        void highlightsExtractsTopAndBottom() {
            givenTwoSegmentsTwoChannels();
            SegmentChannelMatrixResponse res = service.getMatrix(query(null));

            SegmentChannelMatrixResponse.Highlights hl = res.highlights();
            assertThat(hl.topByOpenRate().openRate())
                    .isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(hl.bottomByOpenRate().openRate())
                    .isEqualByComparingTo(new BigDecimal("10.00"));
        }
    }
}
