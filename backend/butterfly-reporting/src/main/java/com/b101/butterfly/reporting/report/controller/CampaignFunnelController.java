package com.b101.butterfly.reporting.report.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelQuery;
import com.b101.butterfly.reporting.report.dto.CampaignFunnelResponse;
import com.b101.butterfly.reporting.report.service.CampaignFunnelCsvExportService;
import com.b101.butterfly.reporting.report.service.CampaignFunnelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final CampaignFunnelService campaignFunnelService;
    private final CampaignFunnelCsvExportService campaignFunnelCsvExportService;

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

    /**
     * S14P31B101-91 — 캠페인 펀널 CSV 다운로드.
     *
     * <p>응답:
     * <ul>
     *     <li>{@code Content-Type: text/csv; charset=UTF-8}</li>
     *     <li>{@code Content-Disposition: attachment; filename*=UTF-8''<RFC5987 인코딩 파일명>}</li>
     *     <li>{@code Cache-Control: no-store}</li>
     *     <li>본문 첫 바이트는 UTF-8 BOM (Excel 한글 인식)</li>
     * </ul>
     *
     * <p>{@code X-Member-Id}는 본 export를 audit하기 위한 행위자 식별자 — auth 모듈 합류 후
     * {@code @AuthenticationPrincipal}로 교체 예정 (다른 컨트롤러와 일관).
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public void exportCsv(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Channel channel,
            @RequestHeader(value = "X-Member-Id", required = false) Long actorMemberId,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        CampaignFunnelQuery query = new CampaignFunnelQuery(projectId, from, to, campaignId, channel);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + buildFilename(projectId));
        response.setHeader("Cache-Control", "no-store");

        PrintWriter writer = response.getWriter();
        campaignFunnelCsvExportService.exportToCsv(
                query, writer, actorMemberId,
                clientIp(request), request.getHeader("User-Agent")
        );
    }

    private static String buildFilename(Long projectId) {
        String ts = LocalDateTime.now().format(FILENAME_TS);
        return URLEncoder.encode("campaign-funnel-" + projectId + "-" + ts + ".csv",
                StandardCharsets.UTF_8);
    }

    private static String clientIp(HttpServletRequest request) {
        // X-Forwarded-For 우선 — proxy 뒤에서 실제 클라이언트 IP 추출.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
