package com.b101.butterfly.payment.domain;

import com.b101.butterfly.global.entity.BaseEntity;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트별 현재 크레딧 잔액 (1 프로젝트 = 1 행).
 *
 * <p>잔액 변동은 반드시 {@link CreditTransaction} append 와 동일 트랜잭션에서 발생해야 한다.
 * 1차 PR 은 row 단위 비관 락(SELECT FOR UPDATE)으로 동시성 보호 — {@code @Version} 필드는
 * 향후 낙관 락 전환을 위해 미리 정의해둔 슬롯이다.
 */
@Entity
@Table(name = "credit_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditAccount extends BaseEntity {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Column(name = "reserved_balance", nullable = false)
    private Long reservedBalance;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private CreditAccount(Long projectId) {
        this.projectId = projectId;
        this.balance = 0L;
        this.reservedBalance = 0L;
    }

    /** 프로젝트별 최초 1회 생성. */
    public static CreditAccount empty(Long projectId) {
        return new CreditAccount(projectId);
    }

    /** 잔액 적립. delta 는 양수만 허용. */
    public long credit(long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("credit delta must be positive: " + delta);
        }
        this.balance += delta;
        return this.balance;
    }

    /** 현재 다른 실행이 선점하지 않은 사용 가능 잔액. */
    public long availableBalance() {
        return this.balance - this.reservedBalance;
    }

    /** 캠페인 발송 전 예상 비용을 선점한다. */
    public long reserve(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("reserve amount must be non-negative: " + amount);
        }
        if (availableBalance() < amount) {
            throw new BusinessException(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
        }
        this.reservedBalance += amount;
        return this.reservedBalance;
    }

    /** 예약만 해제한다. 실제 잔액은 변하지 않는다. */
    public long releaseReserved(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("release amount must be non-negative: " + amount);
        }
        if (this.reservedBalance < amount) {
            throw new IllegalArgumentException("reserved balance is smaller than release amount: " + amount);
        }
        this.reservedBalance -= amount;
        return this.reservedBalance;
    }

    /**
     * 예약 금액을 실제 사용량으로 정산한다.
     *
     * <p>actualAmount가 reservedAmount보다 크면 초과분은 현재 availableBalance에서 추가 차감한다.
     * 다른 캠페인이 잡아 둔 reservedBalance는 침범하지 않는다.
     */
    public long settleReserved(long reservedAmount, long actualAmount) {
        if (reservedAmount < 0 || actualAmount < 0) {
            throw new IllegalArgumentException("settle amounts must be non-negative");
        }
        if (this.reservedBalance < reservedAmount) {
            throw new IllegalArgumentException("reserved balance is smaller than settle amount: " + reservedAmount);
        }
        long extraAmount = Math.max(0L, actualAmount - reservedAmount);
        if (availableBalance() < extraAmount) {
            throw new BusinessException(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
        }
        this.reservedBalance -= reservedAmount;
        this.balance -= actualAmount;
        return this.balance;
    }

    /** 잔액 차감. 잔액 부족 시 PAY_INSUFFICIENT_BALANCE. delta 는 양수(차감량). */
    public long debit(long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("debit delta must be positive: " + delta);
        }
        if (this.balance < delta) {
            throw new BusinessException(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
        }
        this.balance -= delta;
        return this.balance;
    }
}
