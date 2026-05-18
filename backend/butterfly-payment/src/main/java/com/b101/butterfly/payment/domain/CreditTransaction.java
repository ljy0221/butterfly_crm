package com.b101.butterfly.payment.domain;

import com.b101.butterfly.global.entity.BaseEntity;
import com.b101.butterfly.payment.domain.enums.CreditReason;
import com.b101.butterfly.payment.domain.enums.CreditReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크레딧 잔액 변동 원장 (append-only).
 *
 * <p>같은 결제 주문에 대해 {@code (payment_order_id, reason)} UNIQUE 제약이 걸려 있어
 * 중복 적립을 DB 단에서 차단한다. SPEND/ADJUST 처럼 결제 주문과 무관한 경우 paymentOrderId 는 null.
 */
@Entity
@Table(name = "credit_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credit_transaction_id")
    private Long creditTransactionId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 32)
    private CreditReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "delta", nullable = false)
    private Long delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private CreditReason reason;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "memo", length = 500)
    private String memo;

    @Builder
    private CreditTransaction(Long projectId, Long paymentOrderId, Long delta,
                              CreditReferenceType referenceType, Long referenceId,
                              CreditReason reason, Long balanceAfter, String memo) {
        this.projectId = projectId;
        this.paymentOrderId = paymentOrderId;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.delta = delta;
        this.reason = reason;
        this.balanceAfter = balanceAfter;
        this.memo = memo;
    }
}
