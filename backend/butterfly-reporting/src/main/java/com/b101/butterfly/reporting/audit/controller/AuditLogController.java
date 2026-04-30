package com.b101.butterfly.reporting.audit.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.audit.dto.AuditLogDetailResponse;
import com.b101.butterfly.reporting.audit.dto.AuditLogListItem;
import com.b101.butterfly.reporting.audit.dto.AuditLogQuery;
import com.b101.butterfly.reporting.audit.service.AuditLogQueryService;
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
 * 감사 로그 조회 컨트롤러 (API #20, #21 — butterfly_api_spec_v2.md).
 *
 * <p>OWNER 또는 ADMIN 권한 필요. ADMIN의 전체 조회는 {@code projectId} 생략으로 표현되며,
 * OWNER는 자기 프로젝트만 조회 가능하도록 클래스 레벨 SpEL이 막는다.
 *
 * <p>현재 SpEL은 {@code @projectPermission} 빈 도입 전이라 OWNER만 통과하도록 단순화 — ADMIN
 * 처리는 인증 통합 후 별도 PR에서 메서드 레벨로 확장한다.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

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
}
