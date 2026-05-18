package com.b101.butterfly.payment.presentation;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.payment.application.CreditAccountService;
import com.b101.butterfly.payment.application.result.CreditBalanceResult;
import com.b101.butterfly.payment.application.result.CreditTransactionResult;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 크레딧 잔액·원장 조회 REST 진입점 (ADR-0019).
 *
 * <p>OWNER 전용. 잔액 정보는 운영·정산 데이터로 분류되어 EDITOR/VIEWER 노출 보류.
 */
@RestController
@Profile("api")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/credits")
public class CreditController {

    private final CreditAccountService creditAccountService;

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<CreditBalanceResult>> getBalance(
            @PathVariable @Positive Long projectId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(creditAccountService.getBalance(projectId)));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<CreditTransactionResult>>> getTransactions(
            @PathVariable @Positive Long projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<CreditTransactionResult> page =
                creditAccountService.listTransactions(projectId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        )));
    }
}
