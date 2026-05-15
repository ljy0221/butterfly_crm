package com.b101.butterfly.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import org.junit.jupiter.api.Test;

class CreditAccountTest {

    @Test
    void 빈_계정은_잔액_0_으로_시작한다() {
        CreditAccount account = CreditAccount.empty(1L);

        assertThat(account.getProjectId()).isEqualTo(1L);
        assertThat(account.getBalance()).isZero();
    }

    @Test
    void credit_은_잔액을_증가시키고_누적된다() {
        CreditAccount account = CreditAccount.empty(1L);

        long after1 = account.credit(10_000L);
        long after2 = account.credit(30_000L);

        assertThat(after1).isEqualTo(10_000L);
        assertThat(after2).isEqualTo(40_000L);
        assertThat(account.getBalance()).isEqualTo(40_000L);
    }

    @Test
    void credit_delta_가_양수가_아니면_IllegalArgumentException() {
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
    void debit_이_잔액보다_크면_PAY_INSUFFICIENT_BALANCE() {
        CreditAccount account = CreditAccount.empty(1L);
        account.credit(10_000L);

        assertThatThrownBy(() -> account.debit(15_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE);
    }
}
