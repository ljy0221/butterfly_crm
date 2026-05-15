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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private CreditAccount(Long projectId) {
        this.projectId = projectId;
        this.balance = 0L;
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
