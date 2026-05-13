package com.b101.butterfly.reporting.controller;

import com.b101.butterfly.reporting.dto.AnalyticsQuery;
import com.b101.butterfly.reporting.dto.CampaignMetricItem;
import com.b101.butterfly.reporting.dto.CampaignStatsResponse;
import com.b101.butterfly.reporting.dto.ChannelMetricsResponse;
import com.b101.butterfly.reporting.dto.DashboardResponse;
import com.b101.butterfly.reporting.dto.ExecutionStatsResponse;
import com.b101.butterfly.reporting.dto.SegmentMetricsResponse;
import com.b101.butterfly.reporting.service.AnalyticsService;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석/대시보드 API (backend/docs/api/06-analytics.md).
 *
 * <p>Management API — JWT 인증 + 프로젝트 권한 검증은 butterfly-auth가 공통 적용 (ADR-0005).
 * 분석 API는 전부 조회성이므로 클래스 레벨에 VIEWER 최소 권한을 걸고, 엔드포인트별 개별
 * 애너테이션은 생략한다. 향후 EDITOR 이상이 요구되는 엔드포인트가 추가되면 메서드 레벨에서 override.
 */
@RestController
@Profile("api")
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")
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
