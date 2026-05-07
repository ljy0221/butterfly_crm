package com.b101.butterfly.reporting.service;

import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.aggregation.dto.AnalyticsCampaignStatsRow;
import com.b101.butterfly.reporting.aggregation.dto.AnalyticsChannelStatsRow;
import com.b101.butterfly.reporting.aggregation.dto.AnalyticsDailyTrendRow;
import com.b101.butterfly.reporting.aggregation.dto.AnalyticsSummaryRow;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.dto.AnalyticsQuery;
import com.b101.butterfly.reporting.dto.CampaignMetricItem;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.dto.ChannelMetricItem;
import com.b101.butterfly.reporting.dto.ChannelMetricsResponse;
import com.b101.butterfly.reporting.dto.DashboardResponse;
import com.b101.butterfly.reporting.dto.ExecutionStatsResponse;
import com.b101.butterfly.reporting.dto.SegmentMetricItem;
import com.b101.butterfly.reporting.dto.SegmentMetricsResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import com.b101.butterfly.reporting.report.port.CampaignSegmentInfo;
import com.b101.butterfly.reporting.report.port.CampaignSegmentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 분석/대시보드 서비스 실 구현 (S14P31B101-34 + S14P31B101-201).
 *
 * <p>점진 전환: {@code @ConditionalOnProperty(butterfly.analytics.impl=real)}로 활성화되며,
 * 기본 프로파일에서는 {@link AnalyticsServiceStub}이 컨텍스트에 등록되어 있다.
 * 운영/통합 환경에서는 본 구현을 사용하고 테스트/로컬에서는 stub default 유지 (S14P31B101-34 합의).
 *
 * <p>구현 메서드 (모두 ADR-0007 supersede 정합):
 * <ul>
 *   <li>API 57 — {@link #getDashboard}: campaign_stats_daily summary + dailyTrend</li>
 *   <li>API 58 — {@link #getCampaignStats}: 캠페인 누적 합산 (funnel 재활용, S14P31B101-34 본 패턴)</li>
 *   <li>API 59 — {@link #getCampaignMetrics}: (campaignId, channel) 합산 + SPI segmentId 필터</li>
 *   <li>API 60 — {@link #getChannelMetrics}: channel 단위 합산</li>
 *   <li>API 61 — {@link #getSegmentMetrics}: SPI segmentId 환원 후 메모리 GROUP BY</li>
 *   <li>execution stats({@link #getExecutionStats}): campaign_executions JOIN 필요 — 후속 PR.
 *       record 시그니처는 갖춰져 있어 빈 응답 안전 직렬화.</li>
 * </ul>
 *
 * <p>비율 단위: <b>0~100 (퍼센트)</b>. DDL의 {@code DECIMAL(5,2)} 컬럼이 퍼센트로 저장되며
 * SegmentChannelMatrixService 도 동일 단위. 분모 0이면 {@code null} (KPI 정의서 §3).
 */
@Service
@Primary
@ConditionalOnProperty(name = "butterfly.analytics.impl", havingValue = "real", matchIfMissing = false)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    /** KPI 정의서 §3 — 비율 소수점 자리수 통일. */
    private static final int RATE_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CampaignStatsDailyRepository campaignStatsDailyRepository;
    private final CampaignSegmentPort campaignSegmentPort;

    @Override
    public DashboardResponse getDashboard(Long projectId, AnalyticsQuery query) {
        AnalyticsSummaryRow summaryRow = campaignStatsDailyRepository
                .aggregateSummaryByProjectAndDateRange(projectId, query.from(), query.to());
        if (summaryRow == null) {
            summaryRow = AnalyticsSummaryRow.zero();
        }

        List<AnalyticsDailyTrendRow> trendRows = campaignStatsDailyRepository
                .aggregateDailyTrendByProjectAndDateRange(projectId, query.from(), query.to());

        DashboardResponse.Summary summary = new DashboardResponse.Summary(
                summaryRow.campaignsSentInPeriod(),
                summaryRow.totalSent(),
                summaryRow.totalDelivered(),
                summaryRow.totalOpen(),
                summaryRow.totalClicked(),
                summaryRow.totalConvert(),
                summaryRow.totalFailed(),
                rateAsDouble(summaryRow.totalDelivered(), summaryRow.totalSent()),
                rateAsDouble(summaryRow.totalOpen(), summaryRow.totalDelivered()),
                rateAsDouble(summaryRow.totalClicked(), summaryRow.totalOpen()),
                rateAsDouble(summaryRow.totalConvert(), summaryRow.totalSent())
        );

        List<DashboardResponse.DailyPoint> dailyTrend = trendRows.stream()
                .map(r -> new DashboardResponse.DailyPoint(
                        r.date(),
                        r.sent(), r.delivered(), r.opened(), r.clicked(), r.converted()
                ))
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
     *
     * <p>S14P31B101-34에서 도입한 funnel 재활용 패턴을 유지 — 기간 제한 없이 캠페인 전체 누적을
     * 받기 위해 1970~9999 범위로 호출. {@code bucket_date} INDEX 덕분에 비용은 낮다.
     *
     * <p>ADR-0007 비율 4종, 분모 0이면 null. failed는 funnel 쿼리에 포함되지 않아 sent-delivered로 추정.
     */
    @Override
    public CampaignStatsResponse getCampaignStats(Long projectId, Long campaignId) {
        FunnelCountRow row = campaignStatsDailyRepository.aggregateFunnel(
                projectId,
                LocalDate.of(1970, 1, 1),
                LocalDate.of(9999, 12, 31),
                campaignId,
                null  // channel 필터 없음 — 모든 채널 합산
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
                0L,                     // totalTarget — campaign_executions 미연결
                sent, delivered, open, click, convert, failed,
                rateAsDouble(delivered, sent),
                rateAsDouble(open, delivered),
                rateAsDouble(click, open),
                rateAsDouble(convert, sent),
                List.of(),              // executions — campaign_executions SPI 후속
                LocalDateTime.now()
        );
    }

    @Override
    public ExecutionStatsResponse getExecutionStats(Long projectId, Long campaignId, Long executionId) {
        // campaign_executions JOIN + hourly 윈도우 쿼리는 후속 PR 범위.
        // record 시그니처(ADR-0007)는 이미 갖춰져 있어 빈 응답을 안전하게 직렬화.
        return new ExecutionStatsResponse(
                campaignId, executionId, "SCHEDULED",
                0L, 0L, 0L, 0L, 0L, 0L, 0L,
                null, null, null, null,
                null, null,
                LocalDateTime.now()
        );
    }

    @Override
    public PageResponse<CampaignMetricItem> getCampaignMetrics(
            Long projectId, AnalyticsQuery query, int page, int size
    ) {
        Channel channelFilter = parseChannel(query.channel());
        List<AnalyticsCampaignStatsRow> rawRows = campaignStatsDailyRepository
                .aggregateCampaignStatsByProjectAndDateRange(
                        projectId, query.from(), query.to(), channelFilter);

        if (rawRows.isEmpty()) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        // segmentId 필터 / campaignName 매핑은 SPI 호출 한 번으로 일괄 처리.
        List<Long> campaignIds = rawRows.stream()
                .map(AnalyticsCampaignStatsRow::campaignId)
                .distinct()
                .toList();
        Map<Long, CampaignSegmentInfo> segmentMap =
                campaignSegmentPort.findByCampaignIds(campaignIds);

        List<CampaignMetricItem> all = rawRows.stream()
                .filter(r -> matchesSegment(segmentMap.get(r.campaignId()), query.segmentId()))
                .map(r -> toCampaignMetricItem(r, segmentMap.get(r.campaignId())))
                .toList();

        // 페이지네이션 — DB 정렬은 sentCount DESC. 추후 sort 파라미터 합의되면 SQL ORDER BY로 이전.
        int totalElements = all.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<CampaignMetricItem> pageContent = all.subList(fromIndex, toIndex);

        return PageResponse.of(pageContent, page, size, totalElements);
    }

    @Override
    public ChannelMetricsResponse getChannelMetrics(Long projectId, AnalyticsQuery query) {
        List<AnalyticsChannelStatsRow> rawRows = campaignStatsDailyRepository
                .aggregateChannelStatsByProjectAndDateRange(projectId, query.from(), query.to());
        List<ChannelMetricItem> items = rawRows.stream()
                .map(AnalyticsServiceImpl::toChannelMetricItem)
                .toList();
        return new ChannelMetricsResponse(items);
    }

    @Override
    public SegmentMetricsResponse getSegmentMetrics(Long projectId, AnalyticsQuery query) {
        List<AnalyticsCampaignStatsRow> rawRows = campaignStatsDailyRepository
                .aggregateCampaignStatsByProjectAndDateRange(
                        projectId, query.from(), query.to(), null);
        if (rawRows.isEmpty()) {
            return new SegmentMetricsResponse(List.of());
        }

        List<Long> campaignIds = rawRows.stream()
                .map(AnalyticsCampaignStatsRow::campaignId)
                .distinct()
                .toList();
        Map<Long, CampaignSegmentInfo> segmentMap =
                campaignSegmentPort.findByCampaignIds(campaignIds);

        // (segmentId) 단위로 합산 — null segmentId(미지정 캠페인)는 제외.
        Map<Long, SegmentAggregate> grouped = new HashMap<>();
        for (AnalyticsCampaignStatsRow row : rawRows) {
            CampaignSegmentInfo info = segmentMap.get(row.campaignId());
            if (info == null || info.segmentId() == null) {
                continue;
            }
            grouped.merge(
                    info.segmentId(),
                    SegmentAggregate.from(row, info),
                    SegmentAggregate::add
            );
        }

        List<SegmentMetricItem> items = grouped.values().stream()
                .map(AnalyticsServiceImpl::toSegmentMetricItem)
                .sorted(Comparator.comparingLong(SegmentMetricItem::sentCount).reversed())
                .toList();
        return new SegmentMetricsResponse(items);
    }

    // ============================================================
    // 변환 헬퍼
    // ============================================================

    private static Channel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return Channel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean matchesSegment(CampaignSegmentInfo info, Long segmentIdFilter) {
        if (segmentIdFilter == null) {
            return true;
        }
        return info != null && segmentIdFilter.equals(info.segmentId());
    }

    private static CampaignMetricItem toCampaignMetricItem(
            AnalyticsCampaignStatsRow row, CampaignSegmentInfo info
    ) {
        // SPI는 segment 정보만 제공하므로 캠페인 이름은 일단 ID 기반 placeholder.
        // (campaign 모듈 SPI가 campaignName을 노출하면 그때 교체)
        String campaignName = info != null && info.segmentName() != null
                ? "캠페인 " + row.campaignId()
                : "캠페인 " + row.campaignId();
        return new CampaignMetricItem(
                row.campaignId(),
                campaignName,
                row.channel().name(),
                row.sentCount(),
                row.deliveredCount(),
                row.openCount(),
                row.clickedCount(),
                row.convertCount(),
                row.failedCount(),
                rateAsDouble(row.deliveredCount(), row.sentCount()),
                rateAsDouble(row.openCount(), row.deliveredCount()),
                rateAsDouble(row.clickedCount(), row.openCount()),
                rateAsDouble(row.convertCount(), row.sentCount())
        );
    }

    private static ChannelMetricItem toChannelMetricItem(AnalyticsChannelStatsRow row) {
        return new ChannelMetricItem(
                row.channel().name(),
                row.sentCount(),
                row.deliveredCount(),
                row.openCount(),
                row.clickedCount(),
                row.convertCount(),
                row.failedCount(),
                rateAsDouble(row.deliveredCount(), row.sentCount()),
                rateAsDouble(row.openCount(), row.deliveredCount()),
                rateAsDouble(row.clickedCount(), row.openCount()),
                rateAsDouble(row.convertCount(), row.sentCount())
        );
    }

    private static SegmentMetricItem toSegmentMetricItem(SegmentAggregate agg) {
        return new SegmentMetricItem(
                agg.segmentId,
                agg.segmentName,
                agg.campaignIds.size(),
                agg.sentCount,
                agg.deliveredCount,
                agg.openCount,
                agg.clickedCount,
                agg.convertCount,
                rateAsDouble(agg.deliveredCount, agg.sentCount),
                rateAsDouble(agg.openCount, agg.deliveredCount),
                rateAsDouble(agg.clickedCount, agg.openCount),
                rateAsDouble(agg.convertCount, agg.sentCount)
        );
    }

    /** 비율 계산 — 0~100 단위, 분모 0이면 null. SegmentChannelMatrixService와 동일 정책. */
    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** v1 record는 Double을 받으므로 BigDecimal → Double 변환. null 그대로 전달. */
    private static Double rateAsDouble(long numerator, long denominator) {
        BigDecimal r = rate(numerator, denominator);
        return r == null ? null : r.doubleValue();
    }

    // ============================================================
    // segment 합산 누적자
    // ============================================================

    /** segments 응답을 위한 (segmentId) 단위 누적 — campaignId distinct 카운트도 같이. */
    private static final class SegmentAggregate {
        final Long segmentId;
        final String segmentName;
        final java.util.Set<Long> campaignIds;
        long sentCount;
        long deliveredCount;
        long openCount;
        long clickedCount;
        long convertCount;

        private SegmentAggregate(Long segmentId, String segmentName) {
            this.segmentId = segmentId;
            this.segmentName = segmentName;
            this.campaignIds = new java.util.HashSet<>();
        }

        static SegmentAggregate from(AnalyticsCampaignStatsRow row, CampaignSegmentInfo info) {
            SegmentAggregate agg = new SegmentAggregate(info.segmentId(), info.segmentName());
            return agg.addRow(row);
        }

        SegmentAggregate addRow(AnalyticsCampaignStatsRow row) {
            this.campaignIds.add(row.campaignId());
            this.sentCount += row.sentCount();
            this.deliveredCount += row.deliveredCount();
            this.openCount += row.openCount();
            this.clickedCount += row.clickedCount();
            this.convertCount += row.convertCount();
            return this;
        }

        /** Map.merge — 기존 누적자와 신규 1행 누적자를 합친다. */
        SegmentAggregate add(SegmentAggregate other) {
            this.campaignIds.addAll(other.campaignIds);
            this.sentCount += other.sentCount;
            this.deliveredCount += other.deliveredCount;
            this.openCount += other.openCount;
            this.clickedCount += other.clickedCount;
            this.convertCount += other.convertCount;
            return this;
        }
    }
}
