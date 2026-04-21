package com.b101.butterfly.analytics.service;

import com.b101.butterfly.analytics.dto.AnalyticsQuery;
import com.b101.butterfly.analytics.dto.CampaignMetricItem;
import com.b101.butterfly.analytics.dto.CampaignStatsResponse;
import com.b101.butterfly.analytics.dto.ChannelMetricsResponse;
import com.b101.butterfly.analytics.dto.DashboardResponse;
import com.b101.butterfly.analytics.dto.ExecutionStatsResponse;
import com.b101.butterfly.analytics.dto.SegmentMetricsResponse;
import com.b101.butterfly.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 임시 스텁 구현. 집계 테이블 ERD가 확정되면 {@code AnalyticsServiceImpl}로 대체한다.
 *
 * <p>빈 값을 반환하여 다른 팀원의 API 서버 구동을 막지 않기 위한 것이며,
 * 실제 데이터가 필요한 경우 제안서 1-1의 집계 테이블이 먼저 머지되어야 함.
 */
@Service
public class AnalyticsServiceStub implements AnalyticsService {

    @Override
    public DashboardResponse getDashboard(Long projectId, AnalyticsQuery query) {
        return new DashboardResponse(
                new DashboardResponse.Period(query.from(), query.to()),
                new DashboardResponse.Summary(0, 0, 0, 0, 0, 0.0, 0.0),
                List.of(),
                LocalDateTime.now()
        );
    }

    @Override
    public CampaignStatsResponse getCampaignStats(Long projectId, Long campaignId) {
        return new CampaignStatsResponse(
                campaignId, 0L, 0L, 0L, 0L, 0L, 0.0, 0.0,
                List.of(),
                LocalDateTime.now()
        );
    }

    @Override
    public ExecutionStatsResponse getExecutionStats(Long projectId, Long campaignId, Long executionId) {
        return new ExecutionStatsResponse(
                campaignId, executionId, "SCHEDULED",
                0L, 0L, 0L, 0L, 0L, 0.0, 0.0,
                null, null,
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
