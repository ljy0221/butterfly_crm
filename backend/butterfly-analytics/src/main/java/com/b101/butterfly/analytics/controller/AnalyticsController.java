package com.b101.butterfly.analytics.controller;

import com.b101.butterfly.analytics.dto.AnalyticsQuery;
import com.b101.butterfly.analytics.dto.CampaignMetricItem;
import com.b101.butterfly.analytics.dto.CampaignStatsResponse;
import com.b101.butterfly.analytics.dto.ChannelMetricsResponse;
import com.b101.butterfly.analytics.dto.DashboardResponse;
import com.b101.butterfly.analytics.dto.ExecutionStatsResponse;
import com.b101.butterfly.analytics.dto.SegmentMetricsResponse;
import com.b101.butterfly.analytics.service.AnalyticsService;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석/대시보드 API (backend/docs/api/06-analytics.md).
 *
 * <p>Management API — JWT 인증 전제. 인증/인가 필터는 butterfly-auth/butterfly-api에서 공통 적용.
 * 이 컨트롤러는 {@code projectId} 경로 변수를 받지만, 실제 권한 검증은 공통 필터가 처리한다고 전제.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** API 57 */
    @GetMapping("/analytics/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AnalyticsQuery query = new AnalyticsQuery(from, to, null, null);
        return ApiResponse.ok(analyticsService.getDashboard(projectId, query));
    }

    /** API 58 (ADR-0002: 회차 누적 합산) */
    @GetMapping("/campaigns/{campaignId}/stats")
    public ApiResponse<CampaignStatsResponse> getCampaignStats(
            @PathVariable Long projectId,
            @PathVariable Long campaignId
    ) {
        return ApiResponse.ok(analyticsService.getCampaignStats(projectId, campaignId));
    }

    /** 신규 API (ADR-0002): 회차별 상세 통계 */
    @GetMapping("/campaigns/{campaignId}/executions/{executionId}/stats")
    public ApiResponse<ExecutionStatsResponse> getExecutionStats(
            @PathVariable Long projectId,
            @PathVariable Long campaignId,
            @PathVariable Long executionId
    ) {
        return ApiResponse.ok(analyticsService.getExecutionStats(projectId, campaignId, executionId));
    }

    /** API 59 */
    @GetMapping("/analytics/campaigns")
    public ApiResponse<PageResponse<CampaignMetricItem>> getCampaignMetrics(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long segmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AnalyticsQuery query = new AnalyticsQuery(from, to, channel, segmentId);
        return ApiResponse.ok(analyticsService.getCampaignMetrics(projectId, query, page, size));
    }

    /** API 60 */
    @GetMapping("/analytics/channels")
    public ApiResponse<ChannelMetricsResponse> getChannelMetrics(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AnalyticsQuery query = new AnalyticsQuery(from, to, null, null);
        return ApiResponse.ok(analyticsService.getChannelMetrics(projectId, query));
    }

    /** API 61 */
    @GetMapping("/analytics/segments")
    public ApiResponse<SegmentMetricsResponse> getSegmentMetrics(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AnalyticsQuery query = new AnalyticsQuery(from, to, null, null);
        return ApiResponse.ok(analyticsService.getSegmentMetrics(projectId, query));
    }
}
