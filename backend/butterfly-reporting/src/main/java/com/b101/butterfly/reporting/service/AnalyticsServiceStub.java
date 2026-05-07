package com.b101.butterfly.reporting.service;

import com.b101.butterfly.reporting.dto.AnalyticsQuery;
import com.b101.butterfly.reporting.dto.CampaignMetricItem;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.dto.ChannelMetricsResponse;
import com.b101.butterfly.reporting.dto.DashboardResponse;
import com.b101.butterfly.reporting.dto.ExecutionStatsResponse;
import com.b101.butterfly.reporting.dto.SegmentMetricsResponse;
import com.b101.butterfly.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 분석 서비스 스텁 — 기본 빈으로 항상 등록되며, {@code butterfly.analytics.impl=real} property가
 * 설정되면 {@link AnalyticsServiceImpl}이 {@code @Primary}로 override한다 (S14P31B101-34 점진 전환).
 *
 * <p>빈 DB 환경(테스트/로컬)에서 다른 팀원의 API 서버 구동을 막지 않기 위해 빈 응답을 반환한다.
 *
 * <p><b>ADR-0007 supersede 적용:</b> 카운트는 0L, 비율은 {@code null} (분모 0 ⇒ NULL 정책 §3).
 */
@Service
public class AnalyticsServiceStub implements AnalyticsService {

    @Override
    public DashboardResponse getDashboard(Long projectId, AnalyticsQuery query) {
        return new DashboardResponse(
                new DashboardResponse.Period(query.from(), query.to()),
                new DashboardResponse.Summary(
                        0L,        // campaignsSentInPeriod
                        0L, 0L, 0L, 0L, 0L, 0L,        // sent/delivered/open/clicked/convert/failed
                        null, null, null, null         // delivery/open/click/convert rate
                ),
                List.of(),
                LocalDateTime.now()
        );
    }

    @Override
    public CampaignStatsResponse getCampaignStats(Long projectId, Long campaignId) {
        return new CampaignStatsResponse(
                campaignId,
                0L,                                    // totalTarget
                0L, 0L, 0L, 0L, 0L, 0L,                // sent/delivered/open/clicked/convert/failed
                null, null, null, null,                // delivery/open/click/convert rate
                List.of(),
                LocalDateTime.now()
        );
    }

    @Override
    public ExecutionStatsResponse getExecutionStats(Long projectId, Long campaignId, Long executionId) {
        return new ExecutionStatsResponse(
                campaignId, executionId, "SCHEDULED",
                0L,                                    // totalTarget
                0L, 0L, 0L, 0L, 0L, 0L,                // sent/delivered/open/clicked/convert/failed
                null, null, null, null,                // delivery/open/click/convert rate
                null, null,                            // sendStartedAt/sendCompletedAt
                LocalDateTime.now()
        );
    }

    @Override
    public PageResponse<CampaignMetricItem> getCampaignMetrics(Long projectId, AnalyticsQuery query, int page, int size) {
        return PageResponse.of(List.of(), page, size, 0);
    }

    @Override
    public ChannelMetricsResponse getChannelMetrics(Long projectId, AnalyticsQuery query) {
        return new ChannelMetricsResponse(List.of());
    }

    @Override
    public SegmentMetricsResponse getSegmentMetrics(Long projectId, AnalyticsQuery query) {
        return new SegmentMetricsResponse(List.of());
    }
}
