package com.b101.butterfly.reporting.report.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelQuery;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelResponse;
import com.b101.butterfly.reporting.report.service.CampaignFunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 캠페인 단계 펀널 컨트롤러 (S14P31B101-90, 기획서 11-10).
 *
 * <p>플로우 노드별 펀널은 자동화 플로우 도메인(S14P31B101-30) 미구현 상태이므로,
 * 본 PR은 캠페인 발송 lifecycle 단계 펀널만 제공한다.
 *
 * <p>OWNER 권한 필요 — 다른 reports API와 동일한 SpEL 표현식 사용.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports/campaign-funnel")
@RequiredArgsConstructor
public class CampaignFunnelController {

    private final CampaignFunnelService campaignFunnelService;

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ApiResponse<CampaignFunnelResponse> getFunnel(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Channel channel
    ) {
        CampaignFunnelQuery query = new CampaignFunnelQuery(projectId, from, to, campaignId, channel);
        return ApiResponse.ok(campaignFunnelService.getFunnel(query));
    }
}
