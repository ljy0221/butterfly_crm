package com.b101.butterfly.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.domain.enums.PaymentMethod;
import com.b101.butterfly.payment.domain.enums.PaymentStatus;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PaymentOrderTest {

    private PaymentOrder newOrder() {
        return PaymentOrder.builder()
                .projectId(1L)
                .memberId(100L)
                .orderId("ord_test")
                .amount(50_000L)
                .build();
    }

    @Test
    void 빌더로_생성된_주문은_PENDING_상태이고_cancelAmount_0() {
        PaymentOrder order = newOrder();

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getCancelAmount()).isZero();
        assertThat(order.getPaymentKey()).isNull();
    }

    @Test
    void 다른_프로젝트의_접근은_PAY_ORDER_PROJECT_MISMATCH() {
        PaymentOrder order = newOrder();

        assertThatThrownBy(() -> order.assertProjectOwnership(2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_ORDER_PROJECT_MISMATCH);
    }

    @Test
    void 본인_프로젝트는_검증_통과() {
        PaymentOrder order = newOrder();

        order.assertProjectOwnership(1L);
    }

    @Test
    void 이미_CONFIRMED_된_주문의_confirm_은_PAY_ALREADY_CONFIRMED() {
        PaymentOrder order = newOrder();
        order.markConfirmed("paym_xxx", PaymentMethod.CARD, LocalDateTime.now(), "{}");

        assertThatThrownBy(order::assertConfirmable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_ALREADY_CONFIRMED);
    }

    @Test
    void FAILED_상태의_confirm_은_PAY_INVALID_STATUS_FOR_CONFIRM() {
        PaymentOrder order = newOrder();
        order.markFailed("X", "y", "{}");

        assertThatThrownBy(order::assertConfirmable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_INVALID_STATUS_FOR_CONFIRM);
    }

    @Test
    void 금액_불일치는_PAY_AMOUNT_MISMATCH() {
        PaymentOrder order = newOrder();

        assertThatThrownBy(() -> order.assertAmountMatches(49_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_AMOUNT_MISMATCH);
    }

    @Test
    void 금액_일치는_검증_통과() {
        PaymentOrder order = newOrder();

        order.assertAmountMatches(50_000L);
    }

    @Test
    void markConfirmed_는_상태와_PG_식별자를_기록한다() {
        PaymentOrder order = newOrder();
        LocalDateTime approved = LocalDateTime.of(2026, 5, 15, 12, 0);

        order.markConfirmed("paym_abc", PaymentMethod.EASY_PAY, approved, "{\"k\":\"v\"}");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(order.getPaymentKey()).isEqualTo("paym_abc");
        assertThat(order.getMethod()).isEqualTo(PaymentMethod.EASY_PAY);
        assertThat(order.getApprovedAt()).isEqualTo(approved);
        assertThat(order.getRawResponse()).isEqualTo("{\"k\":\"v\"}");
    }
}
