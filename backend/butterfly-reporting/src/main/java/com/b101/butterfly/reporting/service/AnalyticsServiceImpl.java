package com.b101.butterfly.reporting.service;

import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsHourlyRepository;
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
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository.CampaignFunnelRawRow;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final CampaignStatsHourlyRepository campaignStatsHourlyRepository;
    private final ProjectStatsDailyRepository projectStatsDailyRepository;
    private final CampaignSegmentPort campaignSegmentPort;
    private final ReportingNotificationLogRepository notificationLogRepository;
    private final AnalyticsServiceStub stub;

    /**
     * API 57 — 대시보드 요약 + 일자별 추이.
     *
     * <p>{@code project_stats_daily}에서 [from, to] 기간 합산 + 일별 row 시계열.
     */
    @Override
    public DashboardResponse getDashboard(Long projectId, AnalyticsQuery query) {
        LocalDate today = today();
        LocalDate dailyTo = dailyToBeforeToday(query.to(), today);

        PeriodSummaryRow row = PeriodSummaryRow.zero();
        if (isValidRange(query.from(), dailyTo)) {
            PeriodSummaryRow dailyRow = projectStatsDailyRepository.aggregatePeriod(
                    projectId, query.from(), dailyTo);
            row = dailyRow == null ? PeriodSummaryRow.zero() : dailyRow;
        }
        if (containsDate(query, today)) {
            row = plus(row, hourlyProjectSummary(projectId, today));
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

        List<DailyTrendRow> trendRows = isValidRange(query.from(), dailyTo)
                ? projectStatsDailyRepository.findDailyTrend(projectId, query.from(), dailyTo)
                : List.of();
        List<DashboardResponse.DailyPoint> dailyTrend = new ArrayList<>(trendRows.stream()
                .map(t -> new DashboardResponse.DailyPoint(
                        t.date(), t.sent(), t.delivered(), t.opened(), t.clicked(), t.converted()))
                .toList());
        if (containsDate(query, today)) {
            PeriodSummaryRow todayRow = hourlyProjectSummary(projectId, today);
            dailyTrend.add(new DashboardResponse.DailyPoint(
                    today, todayRow.sent(), todayRow.delivered(), todayRow.opened(),
                    todayRow.clicked(), todayRow.converted()));
        }

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
     * <p>1차: {@code campaign_stats_daily} 우선 조회 (KPI 정의서 §5 집계 테이블 우선 정책).
     * 2차: daily 가 비어 있고 (시연 당일·hourly→daily 롤업 전) 실제 발송 row 가 존재하면
     * {@code notification_logs} 를 직접 카운트해 fallback 응답을 만든다. 5/22 시연 직전
     * 부하 테스트 직후처럼 daily 가 아직 안 채워진 시점에도 KPI 카드가 0 으로 나오지 않게
     * 하기 위한 안전장치 (ADR-0007, S14P31B101-34).
     *
     * <p>open/click/convert 도 fallback에서 user_action_logs를 함께 집계한다. ADR-0007 의
     * "분모 0 ⇒ null" 정책은 {@link #rate} 에서 처리.
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

        long sent;
        long delivered;
        long open;
        long click;
        long convert;
        long failed;

        if (row == null || row.sentCount() == 0L) {
            // daily 가 비어 있다 — raw notification_logs fallback. 시연 당일 안전망.
            CampaignFunnelRawRow rawRow = notificationLogRepository.countCampaignFunnel(projectId, campaignId);
            sent = nullSafe(rawRow == null ? null : rawRow.getSentCount());
            delivered = nullSafe(rawRow == null ? null : rawRow.getDeliveredCount());
            failed = nullSafe(rawRow == null ? null : rawRow.getFailedCount());
            open = nullSafe(rawRow == null ? null : rawRow.getOpenCount());
            click = nullSafe(rawRow == null ? null : rawRow.getClickCount());
            convert = nullSafe(rawRow == null ? null : rawRow.getConvertCount());
        } else {
            sent = row.sentCount();
            delivered = row.deliveredCount();
            failed = row.failedCount();
            open = row.openCount();
            click = row.clickCount();
            convert = row.convertCount();
        }

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

    private static long nullSafe(Long v) {
        return v == null ? 0L : v;
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
        LocalDate today = today();
        LocalDate dailyTo = dailyToBeforeToday(query.to(), today);

        // segment 필터 처리 — 92 매트릭스 패턴: segmentId로 캠페인 ID 집합 환원
        List<Long> campaignIdsFilter = null;
        if (query.hasSegmentFilter()) {
            campaignIdsFilter = findCampaignIdsBySegment(projectId, query, query.segmentId());
            if (campaignIdsFilter.isEmpty()) {
                // 해당 세그먼트의 캠페인이 없으면 빈 페이지로 즉시 반환
                return PageResponse.of(List.of(), page, size, 0L);
            }
        }

        int fetchSize = Math.max((page + 1) * size, size);
        List<CampaignMetricRow> dailyRows = new ArrayList<>();
        List<CampaignMetricRow> todayRows = new ArrayList<>();
        if (isValidRange(query.from(), dailyTo)) {
            dailyRows.addAll(campaignStatsDailyRepository.aggregateByCampaign(
                    projectId, query.from(), dailyTo,
                    channelFilter, campaignIdsFilter,
                    PageRequest.of(0, fetchSize)));
        }
        if (containsDate(query, today)) {
            todayRows.addAll(campaignStatsHourlyRepository.aggregateByCampaign(
                    projectId, today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                    channelFilter, campaignIdsFilter,
                    PageRequest.of(0, fetchSize)));
        }

        List<CampaignMetricRow> mergedRows = mergeCampaignRows(dailyRows, todayRows);
        long totalCount = mergedRows.size();
        int fromIndex = Math.min(page * size, mergedRows.size());
        int toIndex = Math.min(fromIndex + size, mergedRows.size());

        List<CampaignMetricItem> content = mergedRows.subList(fromIndex, toIndex).stream()
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
        LocalDate today = today();
        LocalDate dailyTo = dailyToBeforeToday(query.to(), today);

        List<ChannelMetricRow> rows = new ArrayList<>();
        if (isValidRange(query.from(), dailyTo)) {
            rows.addAll(campaignStatsDailyRepository.aggregateByChannel(
                    projectId, query.from(), dailyTo));
        }
        if (containsDate(query, today)) {
            rows.addAll(campaignStatsHourlyRepository.aggregateByChannel(
                    projectId, today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        }

        List<ChannelMetricItem> items = mergeChannelRows(rows).stream()
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

    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");

    private static LocalDate today() {
        return LocalDate.now(DASHBOARD_ZONE);
    }

    private static boolean containsDate(AnalyticsQuery query, LocalDate date) {
        return !date.isBefore(query.from()) && !date.isAfter(query.to());
    }

    private static LocalDate dailyToBeforeToday(LocalDate to, LocalDate today) {
        return to.isBefore(today) ? to : today.minusDays(1);
    }

    private static boolean isValidRange(LocalDate from, LocalDate to) {
        return to != null && !from.isAfter(to);
    }

    private PeriodSummaryRow hourlyProjectSummary(Long projectId, LocalDate date) {
        PeriodSummaryRow row = campaignStatsHourlyRepository.aggregateProjectPeriod(
                projectId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        return row == null ? PeriodSummaryRow.zero() : row;
    }

    private static PeriodSummaryRow plus(PeriodSummaryRow a, PeriodSummaryRow b) {
        return new PeriodSummaryRow(
                a.sent() + b.sent(),
                a.delivered() + b.delivered(),
                a.opened() + b.opened(),
                a.clicked() + b.clicked(),
                a.converted() + b.converted(),
                a.failed() + b.failed(),
                Math.max(a.campaignsSentInPeriod(), b.campaignsSentInPeriod())
        );
    }

    private static List<CampaignMetricRow> mergeCampaignRows(
            List<CampaignMetricRow> dailyRows,
            List<CampaignMetricRow> todayRows) {
        Map<CampaignChannelKey, CampaignMetricAccumulator> grouped = new LinkedHashMap<>();
        for (CampaignMetricRow row : dailyRows) {
            CampaignChannelKey key = new CampaignChannelKey(row.campaignId(), row.channel());
            grouped.computeIfAbsent(key, ignored -> new CampaignMetricAccumulator(row.campaignId(), row.channel()))
                    .add(row, false);
        }
        for (CampaignMetricRow row : todayRows) {
            CampaignChannelKey key = new CampaignChannelKey(row.campaignId(), row.channel());
            grouped.computeIfAbsent(key, ignored -> new CampaignMetricAccumulator(row.campaignId(), row.channel()))
                    .add(row, true);
        }
        return grouped.values().stream()
                .sorted((a, b) -> {
                    int todayCompare = Boolean.compare(b.hasTodayData(), a.hasTodayData());
                    if (todayCompare != 0) {
                        return todayCompare;
                    }
                    int sentCompare = Long.compare(b.sentCount(), a.sentCount());
                    if (sentCompare != 0) {
                        return sentCompare;
                    }
                    return Long.compare(a.campaignId(), b.campaignId());
                })
                .map(CampaignMetricAccumulator::toRow)
                .toList();
    }

    private static List<ChannelMetricRow> mergeChannelRows(List<ChannelMetricRow> rows) {
        Map<Channel, ChannelMetricAccumulator> grouped = new LinkedHashMap<>();
        for (ChannelMetricRow row : rows) {
            grouped.computeIfAbsent(row.channel(), ChannelMetricAccumulator::new).add(row);
        }
        return grouped.values().stream()
                .map(ChannelMetricAccumulator::toRow)
                .sorted(Comparator.comparingLong(ChannelMetricRow::sentCount).reversed())
                .toList();
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

    private record CampaignChannelKey(Long campaignId, String channel) {
    }

    private static final class CampaignMetricAccumulator {
        final Long campaignId;
        final String channel;
        long sent;
        long delivered;
        long open;
        long click;
        long convert;
        long failed;
        boolean hasTodayData;

        CampaignMetricAccumulator(Long campaignId, String channel) {
            this.campaignId = campaignId;
            this.channel = channel;
        }

        Long campaignId() {
            return campaignId;
        }

        long sentCount() {
            return sent;
        }

        boolean hasTodayData() {
            return hasTodayData;
        }

        void add(CampaignMetricRow row, boolean todayData) {
            sent += row.sentCount();
            delivered += row.deliveredCount();
            open += row.openCount();
            click += row.clickedCount();
            convert += row.convertCount();
            failed += row.failedCount();
            hasTodayData = hasTodayData || todayData;
        }

        CampaignMetricRow toRow() {
            return new CampaignMetricRow(
                    campaignId, channel, sent, delivered, open, click, convert, failed);
        }
    }

    private static final class ChannelMetricAccumulator {
        final Channel channel;
        long sent;
        long delivered;
        long open;
        long click;
        long convert;
        long failed;

        ChannelMetricAccumulator(Channel channel) {
            this.channel = channel;
        }

        void add(ChannelMetricRow row) {
            sent += row.sentCount();
            delivered += row.deliveredCount();
            open += row.openCount();
            click += row.clickedCount();
            convert += row.convertCount();
            failed += row.failedCount();
        }

        ChannelMetricRow toRow() {
            return new ChannelMetricRow(channel, sent, delivered, open, click, convert, failed);
        }
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
