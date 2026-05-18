package com.b101.butterfly.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.application.result.PaymentOrderCreateResult;
import com.b101.butterfly.payment.domain.PaymentOrder;
import com.b101.butterfly.payment.domain.PaymentOrderRepository;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import com.b101.butterfly.payment.port.PaymentConfirmResult;
import com.b101.butterfly.payment.port.PaymentGatewayPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentGatewayPort paymentGateway;
    @Mock private PaymentOrderFinalizer paymentOrderFinalizer;

    @InjectMocks private PaymentOrderService paymentOrderService;

    private PaymentOrder pendingOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = PaymentOrder.builder()
                .projectId(1L)
                .memberId(100L)
                .orderId("ord_xxx")
                .amount(50_000L)
                .build();
    }

    @Test
    void createOrder_화이트리스트_외_금액은_PAY_AMOUNT_NOT_ALLOWED() {
        assertThatThrownBy(() -> paymentOrderService.createOrder(1L, 100L, 12_345L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_AMOUNT_NOT_ALLOWED);

        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void createOrder_null_금액은_PAY_AMOUNT_NOT_ALLOWED() {
        assertThatThrownBy(() -> paymentOrderService.createOrder(1L, 100L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createOrder_허용_금액은_orderId_발급_후_저장() {
        PaymentOrderCreateResult result = paymentOrderService.createOrder(1L, 100L, 30_000L);

        assertThat(result.orderId()).startsWith("ord_");
        assertThat(result.amount()).isEqualTo(30_000L);
        verify(paymentOrderRepository, times(1)).save(any(PaymentOrder.class));
    }

    @Test
    void confirm_금액_위변조_시_PG_호출하지_않고_PAY_AMOUNT_MISMATCH() {
        when(paymentOrderRepository.findByOrderId("ord_xxx"))
                .thenReturn(Optional.of(pendingOrder));

        // 클라이언트가 49,999원을 보내지만 서버는 50,000원 저장
        assertThatThrownBy(() ->
                paymentOrderService.confirm(1L, "ord_xxx", "paym_x", 49_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_AMOUNT_MISMATCH);

        verify(paymentGateway, never()).confirm(anyString(), anyString(), anyLong());
        verify(paymentOrderFinalizer, never()).finalizeConfirm(anyLong(), anyString(), any());
    }

    @Test
    void confirm_다른_프로젝트_접근은_PG_호출하지_않고_PAY_ORDER_PROJECT_MISMATCH() {
        when(paymentOrderRepository.findByOrderId("ord_xxx"))
                .thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() ->
                paymentOrderService.confirm(999L, "ord_xxx", "paym_x", 50_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_ORDER_PROJECT_MISMATCH);

        verify(paymentGateway, never()).confirm(anyString(), anyString(), anyLong());
    }

    @Test
    void confirm_정상_흐름은_PG_호출_후_Finalizer_위임() {
        when(paymentOrderRepository.findByOrderId("ord_xxx"))
                .thenReturn(Optional.of(pendingOrder));
        PaymentConfirmResult gatewayResult = new PaymentConfirmResult(
                PaymentConfirmResult.Status.DONE,
                "paym_xxx", null, null, null, null, "{}"
        );
        when(paymentGateway.confirm("ord_xxx", "paym_xxx", 50_000L))
                .thenReturn(gatewayResult);

        paymentOrderService.confirm(1L, "ord_xxx", "paym_xxx", 50_000L);

        verify(paymentGateway, times(1)).confirm("ord_xxx", "paym_xxx", 50_000L);
        verify(paymentOrderFinalizer, times(1))
                .finalizeConfirm(eq(1L), eq("ord_xxx"), eq(gatewayResult));
    }

    @Test
    void confirm_주문이_없으면_PAY_ORDER_NOT_FOUND() {
        when(paymentOrderRepository.findByOrderId("ord_nope"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentOrderService.confirm(1L, "ord_nope", "paym_x", 50_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAY_ORDER_NOT_FOUND);

        verify(paymentGateway, never()).confirm(anyString(), anyString(), anyLong());
    }
}
