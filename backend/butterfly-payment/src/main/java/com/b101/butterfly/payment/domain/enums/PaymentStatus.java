package com.b101.butterfly.payment.domain.enums;

/**
 * 결제 주문의 생명주기 상태.
 *
 * <ul>
 *   <li>{@link #PENDING} — 주문 생성 직후, Toss confirm 호출 전</li>
 *   <li>{@link #CONFIRMED} — Toss 가 DONE 응답을 준 정상 승인</li>
 *   <li>{@link #FAILED} — Toss 거절 또는 클라이언트 위변조 등 비정상 종료</li>
 *   <li>{@link #CANCELLED} — 전체 취소</li>
 *   <li>{@link #PARTIAL_CANCELLED} — 부분 취소 (PR2)</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    PARTIAL_CANCELLED,
}
