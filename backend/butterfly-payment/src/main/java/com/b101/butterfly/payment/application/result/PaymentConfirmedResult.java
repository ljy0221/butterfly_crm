package com.b101.butterfly.payment.application.result;

import com.b101.butterfly.payment.domain.enums.PaymentMethod;
import com.b101.butterfly.payment.domain.enums.PaymentStatus;

/**
 * confirm 처리 결과 — 적립 후 잔액과 최종 결제 상태를 함께 노출.
 */
public record PaymentConfirmedResult(
        String orderId,
        PaymentStatus status,
        PaymentMethod method,
        Long amount,
        Long balanceAfter
) {
}
