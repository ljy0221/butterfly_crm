package com.b101.butterfly.payment.domain.enums;

/**
 * Toss Payments 결제 수단.
 *
 * <p>1차 PR 은 CARD/EASY_PAY 만 화이트리스트. 비동기 수단(VIRTUAL_ACCOUNT, MOBILE_PHONE, TRANSFER)
 * 은 confirm 응답이 즉시 DONE 이 아니라 WAITING_FOR_DEPOSIT 등으로 떨어지므로 PR2 웹훅 구현 후 활성화.
 */
public enum PaymentMethod {
    CARD,
    EASY_PAY,
    VIRTUAL_ACCOUNT,
    MOBILE_PHONE,
    TRANSFER,
    GIFT_CERTIFICATE,
}
