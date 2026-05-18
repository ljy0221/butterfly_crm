package com.b101.butterfly.payment.domain.enums;

/**
 * 크레딧 변동 원장(credit_transaction) 의 변동 사유.
 *
 * <ul>
 *   <li>{@link #CHARGE} — 충전(결제 승인 → 잔액 적립, delta &gt; 0)</li>
 *   <li>{@link #REFUND} — 환불(취소 → 잔액 회수, delta &lt; 0). PR2.</li>
 *   <li>{@link #SPEND} — 사용량 차감(캠페인 발송 등, delta &lt; 0). PR3.</li>
 *   <li>{@link #ADJUST} — 운영자 보정(증감 모두 가능)</li>
 * </ul>
 */
public enum CreditReason {
    CHARGE,
    REFUND,
    SPEND,
    ADJUST,
}
