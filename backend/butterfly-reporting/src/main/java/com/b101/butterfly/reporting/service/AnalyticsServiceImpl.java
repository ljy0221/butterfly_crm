package com.b101.butterfly.reporting.service;

import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository;
import com.b101.butterfly.reporting.dto.AnalyticsQuery;
import com.b101.butterfly.reporting.dto.CampaignMetricItem;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.dto.ChannelMetricsResponse;
import com.b101.butterfly.reporting.dto.DashboardResponse;
import com.b101.butterfly.reporting.dto.ExecutionStatsResponse;
import com.b101.butterfly.reporting.dto.SegmentMetricsResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 분석/대시보드 도메인 서비스 실 구현 (S14P31B101-34).
 *
 * <p>본 PR 스코프: 명세 6개 endpoint 중 핵심 1개({@link #getCampaignStats})만 실 구현하고
 * 나머지는 {@link AnalyticsServiceStub}이 그대로 처리한다. {@code @Primary}로 stub을 override.
 *
 * <p>구현된 endpoint:
 * <ul>
 *   <li>API 58 — {@code GET /campaigns/{campaignId}/stats} — 모든 회차/날짜/채널 누적 합산
 *       (ADR-0002 회차 분리 후의 누적 정의)</li>
 * </ul>
 *
 * <p>비범위 (별도 티켓):
 * <ul>
 *   <li>API 57 (dashboard 일자별 추이) — project_stats_daily 기반, 본인 33번 PR과 결이 다름</li>
 *   <li>API 59 (캠페인 비교 페이지) — 채널/세그먼트 필터 + 페이지네이션</li>
 *   <li>API 60 (channel metrics) — 92번 매트릭스와 중복 가능, 후속 검토</li>
 *   <li>API 61 (segment metrics) — CampaignSegmentPort 활용 (92 패턴)</li>
 *   <li>execution stats — campaign_executions 도메인 SPI 필요</li>
 * </ul>
 *
 * <p>{@code butterfly.analytics.impl=real} property로 toggling — 운영/통합 환경에서는 본 구현,
 * 테스트/로컬에서는 stub 유지 (default). 점진 전환 안전.
 */
@Service
@Primary
@ConditionalOnProperty(name = "butterfly.analytics.impl", havingValue = "real", matchIfMissing = false)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final CampaignStatsDailyRepository campaignStatsDailyRepository;
    private final AnalyticsServiceStub stub;

    /**
     * API 58 — 캠페인 누적 통계 (모든 회차 합산).
     *
     * <p>{@code campaign_stats_daily}에서 해당 campaignId의 모든 row를 SUM으로 합산.
     * 비율 4종은 KPI 정의서(ADR-0007) 기준 — 분모 0이면 null.
     *
     * <p>회차 목록(executions)은 {@link AnalyticsServiceStub}처럼 빈 리스트 — 회차 메타는
     * campaign_executions 도메인이 SPI로 노출해야 가능하므로 후속 작업.
     */
    @Override
    public CampaignStatsResponse getCampaignStats(Long projectId, Long campaignId) {
        // 본 메서드는 기간 제한 없이 캠페인 전체 누적이므로, 매우 넓은 범위로 호출하되
        // bucket_date INDEX가 있어 비용은 낮다.
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
        long failed = Math.max(0L, sent - delivered);  // 실패는 sent - delivered로 추정
                                                        // (campaign_stats_daily에 failed_count가 있지만 funnel 쿼리는 미포함)

        return new CampaignStatsResponse(
                campaignId,
                0L,        // totalTarget — campaign_executions 미연결 (stub과 동일)
                sent, delivered, open, click, convert, failed,
                rate(delivered, sent),   // deliveryRate
                rate(open, delivered),   // openRate (ADR-0007: open/delivered)
                rate(click, open),       // clickRate (ADR-0007: click/open)
                rate(convert, sent),     // convertRate (ADR-0007: convert/sent)
                List.of(),               // executions — campaign_executions SPI 후속
                LocalDateTime.now()
        );
    }

    // --- 나머지 메서드는 stub에 위임 (본 PR 스코프 외) ---

    @Override
    public DashboardResponse getDashboard(Long projectId, AnalyticsQuery query) {
        return stub.getDashboard(projectId, query);
    }

    @Override
    public ExecutionStatsResponse getExecutionStats(Long projectId, Long campaignId, Long executionId) {
        return stub.getExecutionStats(projectId, campaignId, executionId);
    }

    @Override
    public PageResponse<CampaignMetricItem> getCampaignMetrics(Long projectId, AnalyticsQuery query, int page, int size) {
        return stub.getCampaignMetrics(projectId, query, page, size);
    }

    @Override
    public ChannelMetricsResponse getChannelMetrics(Long projectId, AnalyticsQuery query) {
        return stub.getChannelMetrics(projectId, query);
    }

    @Override
    public SegmentMetricsResponse getSegmentMetrics(Long projectId, AnalyticsQuery query) {
        return stub.getSegmentMetrics(projectId, query);
    }

    /**
     * KPI 정의서 §3 — 분모 0이면 비율 정의 불가, null. Double 반환 (ADR-0007 supersede).
     * 백분율 0~100, 소수점 2자리 반올림은 호출자 책임 — 본 메서드는 raw 비율 (0~1) ×100 후 반올림 없이 반환.
     */
    private static Double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        // ADR-0007이 Double로 명시 — 백분율 단위 (0.0~100.0). 소수점은 클라이언트에서 표시 자유.
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }
}
