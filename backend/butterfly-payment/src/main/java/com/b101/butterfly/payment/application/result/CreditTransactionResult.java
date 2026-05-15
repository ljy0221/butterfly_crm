package com.b101.butterfly.payment.application.result;

import com.b101.butterfly.payment.domain.CreditTransaction;
import com.b101.butterfly.payment.domain.enums.CreditReason;
import java.time.LocalDateTime;

public record CreditTransactionResult(
        Long creditTransactionId,
        Long paymentOrderId,
        Long delta,
        CreditReason reason,
        Long balanceAfter,
        String memo,
        LocalDateTime createdAt
) {
    public static CreditTransactionResult from(CreditTransaction tx) {
        return new CreditTransactionResult(
                tx.getCreditTransactionId(),
                tx.getPaymentOrderId(),
                tx.getDelta(),
                tx.getReason(),
                tx.getBalanceAfter(),
                tx.getMemo(),
                tx.getCreatedAt()
        );
    }
}
