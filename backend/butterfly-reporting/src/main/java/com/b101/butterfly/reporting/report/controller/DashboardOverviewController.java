package com.b101.butterfly.reporting.report.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.reporting.report.dto.DashboardOverviewResponse;
import com.b101.butterfly.reporting.report.service.DashboardOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 운영자 대시보드 컨트롤러 (S14P31B101-33).
 *
 * <p>단일 endpoint로 일자 요약 + 실패 TOP N을 한 번에 반환 — 인수 조건 §1
 * "단일 호출로 로드된다, 집계 테이블만 조회".
 *
 * <p>OWNER 권한 필요 (다른 reports와 동일). 명세상 VIEWER 이상이지만 현 SpEL은 OWNER만
 * 통과하므로 일관성 있게 OWNER로 시작 — auth 합류 후 확장 검토.
 *
 * <p>{@code limit}는 1~20 범위로 클램프 — 잘못된 입력으로 본 endpoint를 페이지네이션 우회 통로로
 * 쓰지 못하도록 방어.
 */
@RestController
@Profile("api")
@RequestMapping("/api/v1/projects/{projectId}/dashboard")
@RequiredArgsConstructor
public class DashboardOverviewController {

    private static final int MIN_FAILURE_LIMIT = 1;
    private static final int MAX_FAILURE_LIMIT = 20;

    private final DashboardOverviewService dashboardOverviewService;

    @GetMapping
    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    public ApiResponse<DashboardOverviewResponse> getOverview(
            @PathVariable Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "5") int failureLimit
    ) {
        LocalDate bucketDate = date != null ? date : LocalDate.now();
        int safeLimit = clamp(failureLimit, MIN_FAILURE_LIMIT, MAX_FAILURE_LIMIT);
        return ApiResponse.ok(dashboardOverviewService.getOverview(projectId, bucketDate, safeLimit));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
