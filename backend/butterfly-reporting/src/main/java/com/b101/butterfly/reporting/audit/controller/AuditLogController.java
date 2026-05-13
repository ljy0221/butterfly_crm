package com.b101.butterfly.reporting.audit.controller;

import com.b101.butterfly.auth.security.CustomUserDetails;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.audit.dto.AuditLogDetailResponse;
import com.b101.butterfly.reporting.audit.dto.AuditLogListItem;
import com.b101.butterfly.reporting.audit.dto.AuditLogQuery;
import com.b101.butterfly.reporting.audit.service.AuditLogCsvExportService;
import com.b101.butterfly.reporting.audit.service.AuditLogQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * 감사 로그 조회 컨트롤러 (API #20, #21 — butterfly_api_spec_v2.md).
 *
 * <p>OWNER 또는 ADMIN 권한 필요. ADMIN의 전체 조회는 {@code projectId} 생략으로 표현되며,
 * OWNER는 자기 프로젝트만 조회 가능하도록 클래스 레벨 SpEL이 막는다.
 *
 * <p>현재 SpEL은 {@code @projectPermission} 빈 도입 전이라 OWNER만 통과하도록 단순화 — ADMIN
 * 처리는 인증 통합 후 별도 PR에서 메서드 레벨로 확장한다.
 */
@RestController
@Profile("api")
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AuditLogQueryService auditLogQueryService;
    private final AuditLogCsvExportService auditLogCsvExportService;

    /** API #20 — 감사 로그 목록 조회 */
    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ApiResponse<PageResponse<AuditLogListItem>> getList(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuditLogQuery query = new AuditLogQuery(projectId, memberId, action, targetType, startDate, endDate);
        return ApiResponse.ok(auditLogQueryService.getList(query, page, size));
    }

    /** API #21 — 감사 로그 상세 조회 */
    @GetMapping("/{auditId}")
    public ApiResponse<AuditLogDetailResponse> getDetail(@PathVariable Long auditId) {
        return ApiResponse.ok(auditLogQueryService.getDetail(auditId));
    }

    /**
     * S14P31B101-35 / S14P31B101-91 — 감사 로그 CSV 내보내기.
     *
     * <p>응답:
     * <ul>
     *     <li>{@code Content-Type: text/csv; charset=UTF-8}</li>
     *     <li>{@code Content-Disposition: attachment; filename*=UTF-8''<RFC5987 인코딩 파일명>}</li>
     *     <li>본문 첫 바이트는 UTF-8 BOM (Excel 한글 인식)</li>
     * </ul>
     *
     * <p>매칭 row 50,000건 초과 시 {@code 400 ANALYTICS_003 DATE_RANGE_TOO_LARGE}로 거부.
     * 비동기 방식은 별도 티켓에서 다룬다.
     *
     * <p>행위자 식별자는 {@link CustomUserDetails}에서 추출 — audit 트레일 기록용.
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public void exportCsv(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        Long actorMemberId = userDetails != null ? userDetails.getMemberId() : null;
        AuditLogQuery query = new AuditLogQuery(projectId, memberId, action, targetType, startDate, endDate);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + buildFilename());
        response.setHeader("Cache-Control", "no-store");

        PrintWriter writer = response.getWriter();
        auditLogCsvExportService.exportToCsv(
                query, writer, actorMemberId,
                clientIp(request), request.getHeader("User-Agent")
        );
    }

    private static String buildFilename() {
        String ts = LocalDateTime.now().format(FILENAME_TS);
        return URLEncoder.encode("audit-logs-" + ts + ".csv", StandardCharsets.UTF_8);
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
