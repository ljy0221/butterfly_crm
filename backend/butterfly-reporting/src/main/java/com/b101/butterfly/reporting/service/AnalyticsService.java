package com.b101.butterfly.reporting.service;

import com.b101.butterfly.reporting.dto.AnalyticsQuery;
import com.b101.butterfly.reporting.dto.CampaignMetricItem;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.dto.ChannelMetricsResponse;
import com.b101.butterfly.reporting.dto.DashboardResponse;
import com.b101.butterfly.reporting.dto.ExecutionStatsResponse;
import com.b101.butterfly.reporting.dto.SegmentMetricsResponse;
import com.b101.butterfly.global.response.PageResponse;

/**
 * 분석/대시보드 도메인 서비스 계약.
 *
 * <p>집계 테이블(campaign_metric_daily / channel_metric_daily / segment_metric_daily)
 * ERD가 확정되면 구현체({@code AnalyticsServiceImpl})를 추가한다 — 현재는 인터페이스만.
 */
public interface AnalyticsService {

    /** API 57 */
    DashboardResponse getDashboard(Long projectId, AnalyticsQuery query);

    /** API 58 (ADR-0002: 회차 누적 합산) */
    CampaignStatsResponse getCampaignStats(Long projectId, Long campaignId);

    /** 신규 API (ADR-0002): 회차별 상세 통계 */
    ExecutionStatsResponse getExecutionStats(Long projectId, Long campaignId, Long executionId);

    /** API 59 */
    PageResponse<CampaignMetricItem> getCampaignMetrics(Long projectId, AnalyticsQuery query, int page, int size);

    /** API 60 */
    ChannelMetricsResponse getChannelMetrics(Long projectId, AnalyticsQuery query);

    /** API 61 */
    SegmentMetricsResponse getSegmentMetrics(Long projectId, AnalyticsQuery query);
}
