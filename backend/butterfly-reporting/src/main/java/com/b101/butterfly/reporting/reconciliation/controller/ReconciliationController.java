package com.b101.butterfly.reporting.reconciliation.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationItem;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationQuery;
import com.b101.butterfly.reporting.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 정합성 검증 결과 조회 컨트롤러 (API #24, #25 — butterfly_api_spec_v2.md).
 *
 * <p>ADMIN 전용. 트리거 API(`POST /reconciliations/campaigns/{id}`,
 * `POST .../executions/{id}`)와 자동 배치는 본 티켓 범위 외이며, S14P31B101-187 (126-B)에서
 * 외부 팀 인터페이스 합의 후 추가된다.
 *
 * <p>현재 SpEL은 인증 모듈 미완성 공유 이슈로 ADMIN-only를 표현하는 임시 형태 — 인증 통합 후
 * `@projectPermission` 같은 표준 SpEL bean이 도입되면 그것에 맞춰 갱신한다.
 */
@RestController
@RequestMapping("/api/v1/reconciliations")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /** API #24 — 정합성 검증 결과 목록 (필터 + 페이지네이션) */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ReconciliationItem>> getList(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) Boolean isConsistent,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ReconciliationQuery query = new ReconciliationQuery(
                projectId, campaignId, executionId, isConsistent, startDate, endDate
        );
        return ApiResponse.ok(reconciliationService.getList(query, page, size));
    }

    /** API #25 — 불일치 건만 조회 (consistent=false 강제) */
    @GetMapping("/inconsistent")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ReconciliationItem>> getInconsistent(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(reconciliationService.getInconsistent(
                projectId, campaignId, executionId, startDate, endDate, page, size
        ));
    }
}
