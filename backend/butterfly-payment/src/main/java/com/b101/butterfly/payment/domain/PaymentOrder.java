package com.b101.butterfly.payment.domain;

import com.b101.butterfly.global.entity.BaseEntity;
import com.b101.butterfly.payment.domain.enums.PaymentMethod;
import com.b101.butterfly.payment.domain.enums.PaymentStatus;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 결제 주문/거래 (ADR-0019).
 *
 * <p>생성 시 PENDING → Toss confirm 성공 시 CONFIRMED → 환불 시 CANCELLED/PARTIAL_CANCELLED.
 * orderId 는 서버가 발급한 UUID 기반 식별자로, Toss 결제창 호출과 confirm 매칭에 사용된다.
 * paymentKey 는 Toss 가 confirm 응답에서 돌려주는 그들의 식별자.
 */
@Entity
@Table(name = "payment_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "order_id", nullable = false, length = 64, unique = true)
    private String orderId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 32)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "payment_key", length = 200, unique = true)
    private String paymentKey;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "cancel_amount", nullable = false)
    private Long cancelAmount;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "json")
    private String rawResponse;

    @Builder
    private PaymentOrder(Long projectId, Long memberId, String orderId, Long amount) {
        this.projectId = projectId;
        this.memberId = memberId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.cancelAmount = 0L;
    }

    /** 다른 프로젝트가 이 주문에 접근하려는 시도를 차단 (IDOR 방지). */
    public void assertProjectOwnership(Long requestedProjectId) {
        if (!this.projectId.equals(requestedProjectId)) {
            throw new BusinessException(PaymentErrorCode.PAY_ORDER_PROJECT_MISMATCH);
        }
    }

    /** confirm 호출 전 상태 가드. */
    public void assertConfirmable() {
        if (this.status == PaymentStatus.CONFIRMED) {
            throw new BusinessException(PaymentErrorCode.PAY_ALREADY_CONFIRMED);
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(PaymentErrorCode.PAY_INVALID_STATUS_FOR_CONFIRM);
        }
    }

    /** 클라이언트가 보낸 amount 가 서버 저장 amount 와 일치하는지 검증. */
    public void assertAmountMatches(Long clientAmount) {
        if (!this.amount.equals(clientAmount)) {
            throw new BusinessException(PaymentErrorCode.PAY_AMOUNT_MISMATCH);
        }
    }

    /** Toss confirm 성공 → 승인 정보 기록. */
    public void markConfirmed(String paymentKey, PaymentMethod method,
                              LocalDateTime approvedAt, String rawResponse) {
        this.paymentKey = paymentKey;
        this.method = method;
        this.approvedAt = approvedAt;
        this.rawResponse = rawResponse;
        this.status = PaymentStatus.CONFIRMED;
    }

    /** Toss confirm 실패 → 실패 사유 기록. */
    public void markFailed(String failureCode, String failureMessage, String rawResponse) {
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.rawResponse = rawResponse;
        this.status = PaymentStatus.FAILED;
    }
}
