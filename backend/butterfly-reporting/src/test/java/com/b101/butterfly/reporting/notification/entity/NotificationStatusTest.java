package com.b101.butterfly.reporting.notification.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-175 — status 전이 규칙 단위 테스트.
 */
class NotificationStatusTest {

    @ParameterizedTest(name = "{0} → {1} : {2}")
    @CsvSource({
            // 자기 자신 (멱등)
            "PENDING,   PENDING,   true",
            "SENT,      SENT,      true",
            "DELIVERED, DELIVERED, true",
            "FAILED,    FAILED,    true",
            // PENDING 출발
            "PENDING,   SENT,      true",
            "PENDING,   FAILED,    true",
            "PENDING,   DELIVERED, false",
            // SENT 출발
            "SENT,      DELIVERED, true",
            "SENT,      FAILED,    true",
            "SENT,      PENDING,   false",
            // 종착점은 다른 status로 전이 불가
            "DELIVERED, FAILED,    false",
            "DELIVERED, SENT,      false",
            "DELIVERED, PENDING,   false",
            "FAILED,    DELIVERED, false",
            "FAILED,    SENT,      false",
            "FAILED,    PENDING,   false"
    })
    @DisplayName("canTransitionTo 매트릭스")
    void canTransitionToMatrix(NotificationStatus from, NotificationStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PENDING", "SENT", "DELIVERED", "FAILED"})
    @DisplayName("target=null은 항상 false")
    void nullTargetIsFalse(NotificationStatus from) {
        assertThat(from.canTransitionTo(null)).isFalse();
    }
}
