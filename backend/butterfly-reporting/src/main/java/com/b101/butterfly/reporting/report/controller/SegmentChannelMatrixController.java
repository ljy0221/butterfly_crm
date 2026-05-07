package com.b101.butterfly.reporting.report.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixQuery;
import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixResponse;
import com.b101.butterfly.reporting.report.service.SegmentChannelMatrixCsvExportService;
import com.b101.butterfly.reporting.report.service.SegmentChannelMatrixService;
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
import java.util.Set;

/**
 * 세그먼트 × 채널 성과 매트릭스 컨트롤러 (S14P31B101-92, 기획서 11-10).
 *
 * <p>OWNER 또는 ADMIN 권한 필요 — 본 프로젝트의 segment 데이터에 접근하므로.
 * 현재 SpEL은 기존 컨벤션(`@projectPermission.hasRole`)을 따르며, ADMIN 인가는 SpEL 표현식
 * 자체가 미충족 시 method-level fallback이 없으므로 OWNER 기준만 사용한다.
 *
 * <p>응답은 {@link ApiResponse}로 래핑된 {@link SegmentChannelMatrixResponse}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports/segment-channel-matrix")
@RequiredArgsConstructor
public class SegmentChannelMatrixController {

    private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SegmentChannelMatrixService matrixService;
    private final SegmentChannelMatrixCsvExportService matrixCsvExportService;

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ApiResponse<SegmentChannelMatrixResponse> getMatrix(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Set<Channel> channels,
            @RequestParam(required = false) Set<Long> segmentIds,
            @RequestParam(required = false) SegmentChannelMatrixQuery.SortBy sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean ascending
    ) {
        SegmentChannelMatrixQuery query = new SegmentChannelMatrixQuery(
                projectId, from, to, channels, segmentIds, sortBy, ascending
        );
        return ApiResponse.ok(matrixService.getMatrix(query));
    }

    /**
     * S14P31B101-91 — 세그먼트×채널 매트릭스 CSV 다운로드.
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
            @RequestParam(required = false) Set<Channel> channels,
            @RequestParam(required = false) Set<Long> segmentIds,
            @RequestParam(required = false) SegmentChannelMatrixQuery.SortBy sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean ascending,
            @RequestHeader(value = "X-Member-Id", required = false) Long actorMemberId,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        SegmentChannelMatrixQuery query = new SegmentChannelMatrixQuery(
                projectId, from, to, channels, segmentIds, sortBy, ascending
        );

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + buildFilename(projectId));
        response.setHeader("Cache-Control", "no-store");

        PrintWriter writer = response.getWriter();
        matrixCsvExportService.exportToCsv(
                query, writer, actorMemberId,
                clientIp(request), request.getHeader("User-Agent")
        );
    }

    private static String buildFilename(Long projectId) {
        String ts = LocalDateTime.now().format(FILENAME_TS);
        return URLEncoder.encode("segment-channel-matrix-" + projectId + "-" + ts + ".csv",
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
