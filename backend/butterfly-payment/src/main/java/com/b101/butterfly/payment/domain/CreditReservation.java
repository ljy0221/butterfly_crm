package com.b101.butterfly.payment.domain;

import com.b101.butterfly.global.entity.BaseEntity;
import com.b101.butterfly.payment.domain.enums.CreditReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캠페인 실행 회차 단위 크레딧 예약.
 *
 * <p>동일 프로젝트에서 여러 캠페인이 동시에 발송될 수 있으므로, 발송 직전 예상 비용을
 * campaignExecutionId 기준으로 멱등 예약한다.
 */
@Entity
@Table(name = "credit_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credit_reservation_id")
    private Long creditReservationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "campaign_execution_id", nullable = false)
    private Long campaignExecutionId;

    @Column(name = "expected_count", nullable = false)
    private Integer expectedCount;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "reserved_amount", nullable = false)
    private Long reservedAmount;

    @Column(name = "settled_amount", nullable = false)
    private Long settledAmount;

    @Column(name = "released_amount", nullable = false)
    private Long releasedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CreditReservationStatus status;

    private CreditReservation(
            Long projectId,
            Long campaignId,
            Long campaignExecutionId,
            int expectedCount,
            long unitPrice
    ) {
        this.projectId = projectId;
        this.campaignId = campaignId;
        this.campaignExecutionId = campaignExecutionId;
        this.expectedCount = expectedCount;
        this.unitPrice = unitPrice;
        this.reservedAmount = expectedCount * unitPrice;
        this.settledAmount = 0L;
        this.releasedAmount = 0L;
        this.status = CreditReservationStatus.RESERVED;
    }

    public static CreditReservation reserve(
            Long projectId,
            Long campaignId,
            Long campaignExecutionId,
            int expectedCount,
            long unitPrice
    ) {
        if (expectedCount < 0) {
            throw new IllegalArgumentException("expectedCount must be non-negative: " + expectedCount);
        }
        if (unitPrice <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive: " + unitPrice);
        }
        return new CreditReservation(projectId, campaignId, campaignExecutionId, expectedCount, unitPrice);
    }

    public boolean isClosed() {
        return this.status != CreditReservationStatus.RESERVED;
    }

    public void settle(long actualAmount) {
        if (actualAmount < 0) {
            throw new IllegalArgumentException("actualAmount must be non-negative: " + actualAmount);
        }
        this.settledAmount = actualAmount;
        this.releasedAmount = Math.max(0L, this.reservedAmount - actualAmount);
        this.status = actualAmount == 0L
                ? CreditReservationStatus.RELEASED
                : CreditReservationStatus.SETTLED;
    }

    public void release() {
        this.settledAmount = 0L;
        this.releasedAmount = this.reservedAmount;
        this.status = CreditReservationStatus.RELEASED;
    }
}
