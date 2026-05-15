package com.b101.butterfly.payment.port;

import com.b101.butterfly.payment.domain.enums.PaymentMethod;
import java.time.LocalDateTime;

/**
 * PG confirm 호출 결과 추상화 (ADR-0019).
 *
 * <p>{@link Status#DONE} 만 잔액 적립 대상. {@link Status#FAILED} 는 PG 가 결제를 거절한 경우.
 * {@link Status#OTHER} 는 가상계좌·휴대폰 등 비동기 수단의 미완료 상태 (1차 PR 거절 대상).
 *
 * @param status        confirm 결과 상태 분류
 * @param paymentKey    PG 측 식별자 (DONE 일 때만 의미 있음)
 * @param method        결제 수단 (DONE 일 때만 의미 있음, 매핑 불가 시 null)
 * @param approvedAt    승인 시각
 * @param failureCode   실패 코드 (FAILED 일 때)
 * @param failureMessage 실패 사유 (FAILED 일 때)
 * @param rawResponseJson PG 응답 원본 JSON (감사·디버깅용)
 */
public record PaymentConfirmResult(
        Status status,
        String paymentKey,
        PaymentMethod method,
        LocalDateTime approvedAt,
        String failureCode,
        String failureMessage,
        String rawResponseJson
) {
    public enum Status {
        DONE,
        FAILED,
        OTHER,
    }
}
