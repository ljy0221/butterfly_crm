package com.b101.butterfly.payment.application;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.payment.application.result.PaymentConfirmedResult;
import com.b101.butterfly.payment.application.result.PaymentOrderCreateResult;
import com.b101.butterfly.payment.domain.PaymentOrder;
import com.b101.butterfly.payment.domain.PaymentOrderRepository;
import com.b101.butterfly.payment.domain.enums.CreditChargeTier;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import com.b101.butterfly.payment.port.PaymentConfirmResult;
import com.b101.butterfly.payment.port.PaymentGatewayPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 주문 생성 + Toss confirm orchestration (ADR-0019).
 *
 * <p>confirm 처리 순서:
 * <ol>
 *   <li>주문 조회 (Spring Data 자체 트랜잭션) + IDOR/상태/금액 검증</li>
 *   <li>{@link PaymentGatewayPort#confirm} 호출 (블로킹, DB 락 미보유)</li>
 *   <li>{@link PaymentOrderFinalizer} 가 별도 트랜잭션에서 주문 상태 + 잔액 일관성 갱신</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentOrderFinalizer paymentOrderFinalizer;

    @Transactional
    public PaymentOrderCreateResult createOrder(Long projectId, Long memberId, Long amount) {
        if (amount == null || !CreditChargeTier.isAllowed(amount)) {
            throw new BusinessException(PaymentErrorCode.PAY_AMOUNT_NOT_ALLOWED);
        }
        String orderId = generateOrderId();
        PaymentOrder order = PaymentOrder.builder()
                .projectId(projectId)
                .memberId(memberId)
                .orderId(orderId)
                .amount(amount)
                .build();
        paymentOrderRepository.save(order);
        return new PaymentOrderCreateResult(orderId, amount);
    }

    public PaymentConfirmedResult confirm(Long projectId, String orderId,
                                          String paymentKey, Long clientAmount) {
        long storedAmount = loadAndValidate(projectId, orderId, clientAmount);
        PaymentConfirmResult gatewayResult = paymentGateway.confirm(orderId, paymentKey, storedAmount);
        return paymentOrderFinalizer.finalizeConfirm(projectId, orderId, gatewayResult);
    }

    /**
     * 외부 호출 전 사전 검증.
     *
     * <p>Spring Data 메서드는 자체 트랜잭션을 갖는다. 별도 메서드 분리 + 트랜잭션 어노테이션 없음 —
     * 락도 잡지 않고 검증만 수행하며, 검증 통과 후 트랜잭션은 곧장 종료된다.
     */
    private long loadAndValidate(Long projectId, String orderId, Long clientAmount) {
        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_FOUND));
        order.assertProjectOwnership(projectId);
        order.assertConfirmable();
        order.assertAmountMatches(clientAmount);
        return order.getAmount();
    }

    /** Toss orderId 권장 길이/포맷(6~64자, 영숫자/하이픈/언더스코어). */
    private static String generateOrderId() {
        return "ord_" + UUID.randomUUID().toString().replace("-", "");
    }
}
