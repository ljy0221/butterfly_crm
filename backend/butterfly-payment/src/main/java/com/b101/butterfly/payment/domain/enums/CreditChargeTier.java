package com.b101.butterfly.payment.domain.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 충전 가능한 정액 티어 (ADR-0019 §"금액 검증").
 *
 * <p>주문 생성 시 화이트리스트 검증으로 사용. 자유 입력 대신 정액 티어만 허용해 위변조 시도와
 * 운영 보정 비용을 줄인다.
 */
public enum CreditChargeTier {
    AMOUNT_10K(10_000L),
    AMOUNT_30K(30_000L),
    AMOUNT_50K(50_000L),
    AMOUNT_100K(100_000L),
    AMOUNT_300K(300_000L),
    ;

    private static final Set<Long> ALLOWED = Arrays.stream(values())
            .map(CreditChargeTier::amount)
            .collect(Collectors.toUnmodifiableSet());

    private final long amount;

    CreditChargeTier(long amount) {
        this.amount = amount;
    }

    public long amount() {
        return amount;
    }

    public static boolean isAllowed(long amount) {
        return ALLOWED.contains(amount);
    }
}
