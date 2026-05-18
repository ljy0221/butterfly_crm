package com.b101.butterfly.payment.port;

/**
 * 외부 PG(Payment Gateway) 추상화 인바운드 포트 (ADR-0019, Hexagonal).
 *
 * <p>구현체는 {@code infrastructure/} 에 위치하며 1차 PR 은 {@code TossPaymentsAdapter} 단일.
 * 향후 다른 PG 로 교체/병행 시 어댑터만 추가.
 */
public interface PaymentGatewayPort {

    /**
     * 결제 승인 요청.
     *
     * @param orderId    서버 발급 주문 식별자 (Toss orderId 와 동일하게 전달, Idempotency-Key 로도 사용)
     * @param paymentKey 클라이언트가 받은 PG 측 식별자
     * @param amount     서버 검증 완료된 금액
     */
    PaymentConfirmResult confirm(String orderId, String paymentKey, Long amount);
}
