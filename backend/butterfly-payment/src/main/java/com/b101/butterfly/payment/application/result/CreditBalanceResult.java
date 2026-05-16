package com.b101.butterfly.payment.application.result;

public record CreditBalanceResult(
        Long projectId,
        Long balance,
        Long reservedBalance,
        Long availableBalance
) {
}
