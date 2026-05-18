package com.b101.butterfly.payment.exception.code;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 결제 도메인 에러 코드 (ADR-0019).
 *
 * <p>카테고리:
 * <ul>
 *   <li><b>PAY_001 ~ PAY_005</b> — 입력/주문 검증</li>
 *   <li><b>PAY_006 ~ PAY_009</b> — confirm 단계 검증·정합성</li>
 *   <li><b>PAY_010 ~ PAY_012</b> — Toss 통신/응답 처리</li>
 *   <li><b>PAY_013 ~ PAY_014</b> — 잔액/원장</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    // ==================== 입력/주문 검증 (PAY_001 ~ PAY_005) ====================

    /** 화이트리스트 정액 티어 외 금액 (1차 PR: 10k/30k/50k/100k/300k 만 허용). */
    PAY_AMOUNT_NOT_ALLOWED(
            "PAY_001", "허용되지 않은 충전 금액입니다.", HttpStatus.BAD_REQUEST),

    /** 결제 주문 조회 결과 없음. */
    PAY_ORDER_NOT_FOUND(
            "PAY_002", "결제 주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    /** 다른 프로젝트의 주문에 접근 시도 (IDOR 방지 — 동일 코드 유지). */
    PAY_ORDER_PROJECT_MISMATCH(
            "PAY_003", "결제 주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    /** orderId 중복 — 동일 orderId 로 주문이 이미 존재. */
    PAY_ORDER_DUPLICATE(
            "PAY_004", "이미 존재하는 주문입니다.", HttpStatus.CONFLICT),

    /** 결제 actor(member) 조회 결과 없음. */
    PAY_MEMBER_NOT_FOUND(
            "PAY_005", "결제 요청자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // ==================== confirm 단계 (PAY_006 ~ PAY_009) ====================

    /** 클라이언트가 보낸 amount 가 서버 저장 amount 와 다름 (위변조 의심). */
    PAY_AMOUNT_MISMATCH(
            "PAY_006", "결제 금액이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),

    /** 이미 CONFIRMED 된 주문을 다시 confirm 시도 (멱등 처리됨, 별도 코드 유지). */
    PAY_ALREADY_CONFIRMED(
            "PAY_007", "이미 승인된 결제입니다.", HttpStatus.CONFLICT),

    /** PENDING 이 아닌 주문(FAILED/CANCELLED)에 confirm 시도. */
    PAY_INVALID_STATUS_FOR_CONFIRM(
            "PAY_008", "승인할 수 없는 상태의 주문입니다.", HttpStatus.CONFLICT),

    /** 1차 PR 미지원 결제수단 (가상계좌·휴대폰 등 비동기 수단). */
    PAY_UNSUPPORTED_METHOD(
            "PAY_009", "지원하지 않는 결제 수단입니다.", HttpStatus.UNPROCESSABLE_ENTITY),

    // ==================== Toss 통신 (PAY_010 ~ PAY_012) ====================

    /** Toss API 호출 자체 실패 (네트워크·5xx). */
    PAY_GATEWAY_UNAVAILABLE(
            "PAY_010", "결제 게이트웨이와 통신할 수 없습니다.", HttpStatus.BAD_GATEWAY),

    /** Toss 가 결제 거절 (카드 한도·도난카드 등 4xx). */
    PAY_GATEWAY_REJECTED(
            "PAY_011", "결제가 거절되었습니다.", HttpStatus.PAYMENT_REQUIRED),

    /** Toss 응답 status 가 DONE 도 FAILED 도 아닌 경우 (WAITING_FOR_DEPOSIT 등 미지원 상태). */
    PAY_GATEWAY_UNEXPECTED_STATUS(
            "PAY_012", "결제 응답 상태가 처리 가능한 범위가 아닙니다.", HttpStatus.UNPROCESSABLE_ENTITY),

    // ==================== 잔액/원장 (PAY_013 ~ PAY_014) ====================

    /** 잔액 조회 시 credit_account row 가 존재하지 않음. */
    PAY_CREDIT_ACCOUNT_NOT_FOUND(
            "PAY_013", "크레딧 계정이 존재하지 않습니다.", HttpStatus.NOT_FOUND),

    /** 잔액 부족 (차감 로직은 PR3 — 본 코드는 사전 정의). */
    PAY_INSUFFICIENT_BALANCE(
            "PAY_014", "크레딧 잔액이 부족합니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
