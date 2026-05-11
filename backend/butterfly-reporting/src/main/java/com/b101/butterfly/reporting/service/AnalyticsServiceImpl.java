package com.b101.butterfly.reporting.service;

import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.ProjectStatsDailyRepository;
import com.b101.butterfly.reporting.dto.AnalyticsQuery;
import com.b101.butterfly.reporting.dto.CampaignMetricItem;
import com.b101.butterfly.reporting.dto.CampaignMetricRow;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.dto.ChannelMetricItem;
import com.b101.butterfly.reporting.dto.ChannelMetricRow;
import com.b101.butterfly.reporting.dto.ChannelMetricsResponse;
import com.b101.butterfly.reporting.dto.DailyTrendRow;
import com.b101.butterfly.reporting.dto.DashboardResponse;
import com.b101.butterfly.reporting.dto.ExecutionStatsResponse;
import com.b101.butterfly.reporting.dto.PeriodSummaryRow;
import com.b101.butterfly.reporting.dto.SegmentMetricItem;
import com.b101.butterfly.reporting.dto.SegmentMetricsResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignChannelStatsRow;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import com.b101.butterfly.reporting.report.port.CampaignSegmentInfo;
import com.b101.butterfly.reporting.report.port.CampaignSegmentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 분석/대시보드 도메인 서비스 실 구현 (S14P31B101-34, -202).
 *
 * <p>본 PR 시점에 모든 6개 endpoint 중 5개가 실 구현되었고, execution stats만 stub 유지
 * ({@code campaign_executions} 도메인 SPI 필요).
 *
 * <p>구현된 endpoint:
 * <ul>
 *   <li>API 57 (dashboard) — project_stats_daily 기간 합산 + dailyTrend 시계열</li>
 *   <li>API 58 (campaign stats) — campaign_stats_daily 단일 캠페인 누적</li>
 *   <li>API 59 (campaign metrics 페이지) — (campaignId, channel) GROUP BY + 페이지네이션</li>
 *   <li>API 60 (channel metrics) — (channel) GROUP BY</li>
 *   <li>API 61 (segment metrics) — campaign 단위 합산 후 CampaignSegmentPort로 segment 환원</li>
 * </ul>
 *
 * <p>비범위 (별도 티켓):
 * <ul>
 *   <li>execution stats — campaign_executions 도메인 SPI 필요</li>
 *   <li>캠페인/세그먼트 이름 정규화 — 본 PR은 ID + segment name(SPI) 만, campaignName은 placeholder</li>
 *   <li>revenue (v1 스키마 미존재 — 40 KPI 정의서 §7 명문화)</li>
 * </ul>
 *
 * <p>{@code butterfly.analytics.impl=real} property로 toggling — 운영/통합에서는 본 구현,
 * 테스트/로컬은 stub 유지 (default). 점진 전환 안전.
 */
@Service
@Primary
@ConditionalOnProperty(name = "butterfly.analytics.impl", havingValue = "real", matchIfMissing = false)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final CampaignStatsDailyRepository campaignStatsDailyRepository;
    private final ProjectStatsDailyRepository projectStatsDailyRepository;
    private final CampaignSegmentPort campaignSegmentPort;
    private final AnalyticsServiceStub stub;

    /**
     * API 57 — 대시보드 요약 + 일자별 추이.
     *
     * <p>{@code project_stats_daily}에서 [from, to] 기간 합산 + 일별 row 시계열.
     */
    @Override
    public DashboardResponse getDashboard(Long projectId, AnalyticsQuery query) {
        PeriodSummaryRow row = projectStatsDailyRepository.aggregatePeriod(
                projectId, query.from(), query.to());
        if (row == null) {
            row = PeriodSummaryRow.zero();
        }

        DashboardResponse.Summary summary = new DashboardResponse.Summary(
                row.campaignsSentInPeriod(),
                row.sent(),
                row.delivered(),
                row.opened(),
                row.clicked(),
                row.converted(),
                row.failed(),
                rate(row.delivered(), row.sent()),       // avgDeliveryRate
                rate(row.opened(), row.delivered()),     // avgOpenRate (ADR-0007)
                rate(row.clicked(), row.opened()),       // avgClickRate (ADR-0007: click/open)
                rate(row.converted(), row.sent())        // avgConvertRate (ADR-0007)
        );

        List<DailyTrendRow> trendRows = projectStatsDailyRepository.findDailyTrend(
                projectId, query.from(), query.to());
        List<DashboardResponse.DailyPoint> dailyTrend = trendRows.stream()
                .map(t -> new DashboardResponse.DailyPoint(
                        t.date(), t.sent(), t.delivered(), t.opened(), t.clicked(), t.converted()))
                .toList();

        return new DashboardResponse(
                new DashboardResponse.Period(query.from(), query.to()),
                summary,
                dailyTrend,
                LocalDateTime.now()
        );
    }

    /**
     * API 58 — 캠페인 누적 통계 (모든 회차 합산).
     */
    @Override
    public CampaignStatsResponse getCampaignStats(Long projectId, Long campaignId) {
        FunnelCountRow row = campaignStatsDailyRepository.aggregateFunnel(
                projectId,
                LocalDate.of(1970, 1, 1),
                LocalDate.of(9999, 12, 31),
                campaignId,
                null
        );
        if (row == null) {
            row = FunnelCountRow.zero();
        }

        long sent = row.sentCount();
        long delivered = row.deliveredCount();
        long open = row.openCount();
        long click = row.clickCount();
        long convert = row.convertCount();
        long failed = Math.max(0L, sent - delivered);

        return new CampaignStatsResponse(
                campaignId,
                0L,
                sent, delivered, open, click, convert, failed,
                rate(delivered, sent),
                rate(open, delivered),
                rate(click, open),
                rate(convert, sent),
                List.of(),
                LocalDateTime.now()
        );
    }

    @Override
    public ExecutionStatsResponse getExecutionStats(Long projectId, Long campaignId, Long executionId) {
        // campaign_executions 도메인 SPI 필요 — 후속 티켓에서 구현. 현 시점은 stub.
        return stub.getExecutionStats(projectId, campaignId, executionId);
    }

    /**
     * API 59 — 캠페인 비교 페이지. (campaignId, channel) 쌍 단위.
     */
    @Override
    public PageResponse<CampaignMetricItem> getCampaignMetrics(
            Long projectId, AnalyticsQuery query, int page, int size) {
        Channel channelFilter = parseChannel(query.channel());

        // segment 필터 처리 — 92 매트릭스 패턴: segmentId로 캠페인 ID 집합 환원
        List<Long> campaignIdsFilter = null;
        if (query.hasSegmentFilter()) {
            campaignIdsFilter = findCampaignIdsBySegment(projectId, query, query.segmentId());
            if (campaignIdsFilter.isEmpty()) {
                // 해당 세그먼트의 캠페인이 없으면 빈 페이지로 즉시 반환
                return PageResponse.of(List.of(), page, size, 0L);
            }
        }

        long totalCount = campaignStatsDailyRepository.countCampaignChannelPairs(
                projectId, query.from(), query.to(), channelFilter, campaignIdsFilter);

        List<CampaignMetricRow> rows = campaignStatsDailyRepository.aggregateByCampaign(
                projectId, query.from(), query.to(),
                channelFilter, campaignIdsFilter,
                PageRequest.of(page, size));

        List<CampaignMetricItem> content = rows.stream()
                .map(r -> new CampaignMetricItem(
                        r.campaignId(),
                        // 캠페인 이름은 SPI 미연결 — 본 PR은 placeholder
                        "campaign-" + r.campaignId(),
                        r.channel(),
                        r.sentCount(),
                        r.deliveredCount(),
                        r.openCount(),
                        r.clickedCount(),
                        r.convertCount(),
                        r.failedCount(),
                        rate(r.deliveredCount(), r.sentCount()),
                        rate(r.openCount(), r.deliveredCount()),
                        rate(r.clickedCount(), r.openCount()),
                        rate(r.convertCount(), r.sentCount())
                ))
                .toList();

        return PageResponse.of(content, page, size, totalCount);
    }

    /**
     * API 60 — 채널별 성과.
     */
    @Override
    public ChannelMetricsResponse getChannelMetrics(Long projectId, AnalyticsQuery query) {
        List<ChannelMetricRow> rows = campaignStatsDailyRepository.aggregateByChannel(
                projectId, query.from(), query.to());

        List<ChannelMetricItem> items = rows.stream()
                .map(r -> new ChannelMetricItem(
                        r.channel().name(),
                        r.sentCount(),
                        r.deliveredCount(),
                        r.openCount(),
                        r.clickedCount(),
                        r.convertCount(),
                        r.failedCount(),
                        rate(r.deliveredCount(), r.sentCount()),
                        rate(r.openCount(), r.deliveredCount()),
                        rate(r.clickedCount(), r.openCount()),
                        rate(r.convertCount(), r.sentCount())
                ))
                .toList();

        return new ChannelMetricsResponse(items);
    }

    /**
     * API 61 — 세그먼트별 성과.
     *
     * <p>{@code campaign_stats_daily}를 (campaignId) 단위로 집계 후 {@link CampaignSegmentPort}로
     * segment 환원. 92 매트릭스의 segment 그룹화 패턴 그대로지만 채널 차원 없이 합산.
     */
    @Override
    public SegmentMetricsResponse getSegmentMetrics(Long projectId, AnalyticsQuery query) {
        // 1. (campaignId, channel) 단위 raw rows
        List<CampaignChannelStatsRow> rawRows = campaignStatsDailyRepository
                .aggregateByProjectAndDateRange(projectId, query.from(), query.to());
        if (rawRows.isEmpty()) {
            return new SegmentMetricsResponse(List.of());
        }

        // 2. campaignId -> segment 매핑
        List<Long> campaignIds = rawRows.stream()
                .map(CampaignChannelStatsRow::campaignId).distinct().toList();
        Map<Long, CampaignSegmentInfo> segmentMap =
                campaignSegmentPort.findByCampaignIds(campaignIds);

        // 3. (segmentId) 단위로 재합산 — 채널 차원은 합쳐짐, 미지정 캠페인은 segmentId=null로 그룹
        Map<SegmentKey, SegmentAccumulator> grouped = new HashMap<>();
        for (CampaignChannelStatsRow row : rawRows) {
            CampaignSegmentInfo info = segmentMap.get(row.campaignId());
            Long segId = info == null ? null : info.segmentId();
            String segName = info == null ? null : info.segmentName();
            SegmentKey key = new SegmentKey(segId, segName);
            grouped.computeIfAbsent(key, k -> new SegmentAccumulator()).add(row);
        }

        List<SegmentMetricItem> items = new ArrayList<>(grouped.size());
        for (Map.Entry<SegmentKey, SegmentAccumulator> e : grouped.entrySet()) {
            SegmentKey k = e.getKey();
            SegmentAccumulator a = e.getValue();
            items.add(new SegmentMetricItem(
                    k.segmentId(),
                    k.segmentName(),
                    a.campaignIds.size(),
                    a.sent, a.delivered, a.open, a.click, a.convert,
                    rate(a.delivered, a.sent),
                    rate(a.open, a.delivered),
                    rate(a.click, a.open),
                    rate(a.convert, a.sent)
            ));
        }
        // sentCount DESC 정렬 — 클라이언트가 실적 큰 세그먼트부터 보게
        items.sort((x, y) -> Long.compare(y.sentCount(), x.sentCount()));
        return new SegmentMetricsResponse(items);
    }

    /**
     * segment 필터 적용 시 해당 segment의 campaignId 집합을 추출. 92 매트릭스 패턴과 동일.
     *
     * <p>현재 데이터 모델에서 segmentId → campaignIds 역인덱스가 없으므로, 기간 내 활동 캠페인을
     * 한 번 가져와 segmentId로 필터한다 — 운영상 작은 비용.
     */
    private List<Long> findCampaignIdsBySegment(Long projectId, AnalyticsQuery query, Long segmentId) {
        List<CampaignChannelStatsRow> rawRows = campaignStatsDailyRepository
                .aggregateByProjectAndDateRange(projectId, query.from(), query.to());
        List<Long> campaignIds = rawRows.stream()
                .map(CampaignChannelStatsRow::campaignId).distinct().toList();
        if (campaignIds.isEmpty()) {
            return List.of();
        }
        Map<Long, CampaignSegmentInfo> segmentMap =
                campaignSegmentPort.findByCampaignIds(campaignIds);
        return segmentMap.values().stream()
                .filter(info -> segmentId.equals(info.segmentId()))
                .map(CampaignSegmentInfo::campaignId)
                .toList();
    }

    private static Channel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return Channel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 알 수 없는 채널 — 결과 0건이 자연스러우므로 매칭 안 되는 sentinel
            return null;
        }
    }

    /** 분모 0이면 null (KPI 정의서 §3, ADR-0007). */
    private static Double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    /** 그룹화 키 — segmentId가 null이면 미지정 캠페인 그룹. record로 equals/hashCode 자동. */
    private record SegmentKey(Long segmentId, String segmentName) {
    }

    /**
     * segment별 누적기. 캠페인 ID 집합도 함께 모아 distinct count 계산.
     */
    private static final class SegmentAccumulator {
        long sent;
        long delivered;
        long failed;
        long open;
        long click;
        long convert;
        final java.util.HashSet<Long> campaignIds = new java.util.HashSet<>();

        void add(CampaignChannelStatsRow row) {
            sent += row.sentCount();
            delivered += row.deliveredCount();
            failed += row.failedCount();
            open += row.openCount();
            click += row.clickCount();
            convert += row.convertCount();
            campaignIds.add(row.campaignId());
        }
    }
}
