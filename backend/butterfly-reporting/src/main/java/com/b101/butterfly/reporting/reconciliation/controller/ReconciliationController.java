package com.b101.butterfly.reporting.reconciliation.controller;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationItem;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationQuery;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationTriggerResponse;
import com.b101.butterfly.reporting.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 정합성 검증 결과 조회 + 트리거 컨트롤러 (API #22, #23, #24, #25 — butterfly_api_spec_v2.md).
 *
 * <p>ADMIN 전용. 자동 배치(매일 새벽 2시)는 본 컨트롤러와 별개로
 * {@code ReconciliationScheduler}(S14P31B101-187 PR-2)에서 처리되며, 본 컨트롤러는 운영자가
 * 즉시 검증을 강제 실행할 수 있는 트리거 API와 결과 조회 API를 제공한다.
 *
 * <p>현재 SpEL은 인증 모듈 미완성 공유 이슈로 ADMIN-only를 표현하는 임시 형태 — 인증 통합 후
 * `@projectPermission` 같은 표준 SpEL bean이 도입되면 그것에 맞춰 갱신한다.
 */
@RestController
@RequestMapping("/api/v1/reconciliations")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * API #22 — 캠페인의 모든 회차 즉시 정합성 검증 (S14P31B101-187 트리거).
     *
     * <p>회차 미존재(0건)여도 200을 반환한다 — {@code checked=0} 으로 표현. 캠페인 자체 미존재는
     * 외부 SPI({@code CampaignExecutionPort#findByCampaign})가 빈 리스트를 반환하므로
     * 동일하게 처리된다.
     */
    @PostMapping("/campaigns/{campaignId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReconciliationTriggerResponse> reconcileCampaign(
            @PathVariable Long campaignId
    ) {
        List<ReconciliationItem> items = reconciliationService.reconcileCampaign(campaignId);
        return ApiResponse.ok(ReconciliationTriggerResponse.from(items));
    }

    /**
     * API #23 — 단일 회차 즉시 정합성 검증 (S14P31B101-187 트리거).
     *
     * <p>회차 미존재 시 {@code 404 EXECUTION_NOT_FOUND} (AnalyticsErrorCode).
     */
    @PostMapping("/executions/{executionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReconciliationItem> reconcileExecution(
            @PathVariable Long executionId
    ) {
        return ApiResponse.ok(reconciliationService.reconcileExecution(executionId));
    }

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
