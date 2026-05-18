package com.b101.butterfly.payment.presentation;

import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.payment.application.PaymentOrderService;
import com.b101.butterfly.payment.application.result.PaymentConfirmedResult;
import com.b101.butterfly.payment.application.result.PaymentOrderCreateResult;
import com.b101.butterfly.payment.presentation.dto.PaymentConfirmRequest;
import com.b101.butterfly.payment.presentation.dto.PaymentOrderCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제(주문 생성 + Toss confirm) REST 진입점 (ADR-0019).
 *
 * <p>모든 엔드포인트 OWNER 전용. memberId 는 SecurityContext 의 {@code Authentication#getName()}
 * 에서 추출 (campaign 모듈과 동일 패턴, X-Member-Id 헤더 미신뢰).
 */
@RestController
@Profile("api")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/payments")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PaymentOrderCreateResult>> createOrder(
            @PathVariable @Positive Long projectId,
            @RequestBody @Valid PaymentOrderCreateRequest request
    ) {
        PaymentOrderCreateResult result = paymentOrderService.createOrder(
                projectId, currentMemberId(), request.amount());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result));
    }

    @PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentConfirmedResult>> confirm(
            @PathVariable @Positive Long projectId,
            @RequestBody @Valid PaymentConfirmRequest request
    ) {
        PaymentConfirmedResult result = paymentOrderService.confirm(
                projectId, request.orderId(), request.paymentKey(), request.amount());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private Long currentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
