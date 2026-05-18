package com.b101.butterfly.payment.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreditChargeTierTest {

    @ParameterizedTest
    @ValueSource(longs = { 10_000L, 30_000L, 50_000L, 100_000L, 300_000L })
    void 화이트리스트_정액_티어는_허용된다(long allowedAmount) {
        assertThat(CreditChargeTier.isAllowed(allowedAmount)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L, 9_999L, 10_001L, 200_000L, 500_000L, 1_000_000L })
    void 정액_티어_외_금액은_거절된다(long outsideAmount) {
        assertThat(CreditChargeTier.isAllowed(outsideAmount)).isFalse();
    }

    @Test
    void 음수_금액도_거절된다() {
        assertThat(CreditChargeTier.isAllowed(-10_000L)).isFalse();
    }
}
