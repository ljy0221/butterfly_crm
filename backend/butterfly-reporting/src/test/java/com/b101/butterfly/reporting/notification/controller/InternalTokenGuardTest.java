package com.b101.butterfly.reporting.notification.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenGuardTest {

    @Test
    @DisplayName("토큰 일치 시 예외 없음")
    void verifyAcceptsMatch() {
        InternalTokenGuard guard = new InternalTokenGuard("secret-token");

        guard.verify("secret-token"); // does not throw
    }

    @Test
    @DisplayName("토큰 불일치 시 401")
    void verifyRejectsMismatch() {
        InternalTokenGuard guard = new InternalTokenGuard("secret-token");

        assertThatThrownBy(() -> guard.verify("wrong-token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    @DisplayName("헤더 누락 시 401")
    void verifyRejectsMissingHeader() {
        InternalTokenGuard guard = new InternalTokenGuard("secret-token");

        assertThatThrownBy(() -> guard.verify(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    @DisplayName("토큰 미설정 환경에서는 503 (운영 사고 방지)")
    void verifyFailsWhenTokenNotConfigured() {
        InternalTokenGuard guard = new InternalTokenGuard("");

        assertThatThrownBy(() -> guard.verify("any"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(503));
    }
}
