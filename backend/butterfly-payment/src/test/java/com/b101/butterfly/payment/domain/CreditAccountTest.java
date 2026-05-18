package com.b101.butterfly.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import org.junit.jupiter.api.Test;

class CreditAccountTest {

    @Test
    void 빈_계정은_잔액과_예약잔액_0으로_시작한다() {
        CreditAccount account = CreditAccount.empty(1L);

        assertThat(account.getProjectId()).isEqualTo(1L);
        assertThat(account.getBalance()).isZero();
        assertThat(account.getReservedBalance()).isZero();
        assertThat(account.availableBalance()).isZero();
    }

    @Test
    void credit_은_잔액을_증가시킨다() {
        CreditAccount account = CreditAccount.empty(1L);

        long after1 = account.credit(10_000L);
        long after2 = account.credit(30_000L);

        assertThat(after1).isEqualTo(10_000L);
        assertThat(after2).isEqualTo(40_000L);
        assertThat(account.getBalance()).isEqualTo(40_000L);
    }

    @Test
    void credit_delta가_양수가_아니면_IllegalArgumentException() {
        CreditAccount account = CreditAccount.empty(1L);

        assertThatThrownBy(() -> account.credit(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debit_은_잔액을_차감한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(50_000L);

        long after = account.debit(20_000L);

        assertThat(after).isEqualTo(30_000L);
        assertThat(account.getBalance()).isEqualTo(30_000L);
    }

    @Test
    void debit_금액이_잔액보다_크면_PAY_INSUFFICIENT_BALANCE() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);

        assertThatThrownBy(() -> account.debit(15_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
    }

    @Test
    void reserve_는_사용가능_잔액만_선점한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);

        account.reserve(7_000L);

        assertThat(account.getBalance()).isEqualTo(10_000L);
        assertThat(account.getReservedBalance()).isEqualTo(7_000L);
        assertThat(account.availableBalance()).isEqualTo(3_000L);
        assertThatThrownBy(() -> account.reserve(4_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
    }

    @Test
    void releaseReserved_는_예약만_해제하고_잔액은_그대로_둔다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        account.reserve(7_000L);

        account.releaseReserved(2_000L);

        assertThat(account.getBalance()).isEqualTo(10_000L);
        assertThat(account.getReservedBalance()).isEqualTo(5_000L);
        assertThat(account.availableBalance()).isEqualTo(5_000L);
    }

    @Test
    void settleReserved_는_실제_사용량만_차감하고_남은_예약을_해제한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        account.reserve(7_000L);

        long balanceAfter = account.settleReserved(7_000L, 5_000L);

        assertThat(balanceAfter).isEqualTo(5_000L);
        assertThat(account.getReservedBalance()).isZero();
        assertThat(account.availableBalance()).isEqualTo(5_000L);
    }

    @Test
    void settleReserved_는_초과_발송분을_사용가능_잔액에서_추가_차감한다() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);
        account.reserve(7_000L);

        long balanceAfter = account.settleReserved(7_000L, 8_000L);

        assertThat(balanceAfter).isEqualTo(2_000L);
        assertThat(account.getReservedBalance()).isZero();
        assertThat(account.availableBalance()).isEqualTo(2_000L);
    }
}
