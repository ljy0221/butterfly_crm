package com.b101.butterfly.payment.application;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.application.result.PaymentConfirmedResult;
import com.b101.butterfly.payment.domain.PaymentOrder;
import com.b101.butterfly.payment.domain.PaymentOrderRepository;
import com.b101.butterfly.payment.domain.enums.PaymentMethod;
import com.b101.butterfly.payment.domain.enums.PaymentStatus;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import com.b101.butterfly.payment.port.PaymentConfirmResult;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PG 응답을 받은 직후 짧은 트랜잭션 안에서 주문 상태·잔액을 일관성 있게 갱신.
 *
 * <p>PaymentOrderService 와 분리한 이유: Spring AOP 프록시는 같은 클래스 내부 호출에서 동작하지 않아
 * {@code @Transactional} 경계가 무시된다. 외부 호출 전후의 트랜잭션 분리를 보장하려면 호출자를 다른
 * 컴포넌트로 두어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderFinalizer {

    private static final Set<PaymentMethod> SUPPORTED_METHODS =
            EnumSet.of(PaymentMethod.CARD, PaymentMethod.EASY_PAY);

    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditAccountService creditAccountService;

    @Transactional
    public PaymentConfirmedResult finalizeConfirm(Long projectId, String orderId,
                                                  PaymentConfirmResult gatewayResult) {
        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_FOUND));
        order.assertProjectOwnership(projectId);

        return switch (gatewayResult.status()) {
            case DONE -> handleDone(order, gatewayResult);
            case FAILED -> handleFailed(order, gatewayResult);
            case OTHER -> handleUnexpected(order, gatewayResult);
        };
    }

    private PaymentConfirmedResult handleDone(PaymentOrder order, PaymentConfirmResult result) {
        PaymentMethod method = result.method();
        if (method == null || !SUPPORTED_METHODS.contains(method)) {
            order.markFailed("UNSUPPORTED_METHOD",
                    "1차 PR 미지원 결제수단: " + method,
                    result.rawResponseJson());
            log.warn("Payment confirmed by PG but method unsupported orderId={} method={}",
                    order.getOrderId(), method);
            throw new BusinessException(PaymentErrorCode.PAY_UNSUPPORTED_METHOD);
        }
        order.markConfirmed(result.paymentKey(), method, result.approvedAt(), result.rawResponseJson());
        long balanceAfter = creditAccountService.creditFromPayment(
                order.getProjectId(), order.getPaymentOrderId(), order.getAmount());
        return new PaymentConfirmedResult(
                order.getOrderId(),
                PaymentStatus.CONFIRMED,
                method,
                order.getAmount(),
                balanceAfter
        );
    }

    private PaymentConfirmedResult handleFailed(PaymentOrder order, PaymentConfirmResult result) {
        order.markFailed(result.failureCode(), result.failureMessage(), result.rawResponseJson());
        throw new BusinessException(PaymentErrorCode.PAY_GATEWAY_REJECTED);
    }

    private PaymentConfirmedResult handleUnexpected(PaymentOrder order, PaymentConfirmResult result) {
        order.markFailed("UNEXPECTED_STATUS", result.failureMessage(), result.rawResponseJson());
        throw new BusinessException(PaymentErrorCode.PAY_GATEWAY_UNEXPECTED_STATUS);
    }
}
