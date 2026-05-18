package com.b101.butterfly.payment.application.result;

/**
 * 주문 생성 결과 — 프론트가 Toss 결제창을 띄울 때 사용하는 식별자/금액 노출.
 */
public record PaymentOrderCreateResult(
        String orderId,
        Long amount
) {
}
