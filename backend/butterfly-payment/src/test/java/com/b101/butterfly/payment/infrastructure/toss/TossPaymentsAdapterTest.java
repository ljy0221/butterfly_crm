package com.b101.butterfly.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Toss confirm 호출의 Basic Auth 헤더 형식 회귀 테스트.
 *
 * <p>{@code base64(secretKey + ":")} 의 콜론 누락은 Toss 통합의 가장 흔한 버그라 별도 검증.
 */
class TossPaymentsAdapterTest {

    @Test
    void basic_auth_헤더는_secretKey_콜론_을_base64_인코딩한다() {
        String secret = "test_sk_abc123";

        String header = TossPaymentsAdapter.buildBasicAuthHeader(secret);

        assertThat(header).startsWith("Basic ");
        String encoded = header.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(secret + ":");
    }

    @Test
    void secretKey_콜론_없이_인코딩_되지_않는다() {
        String secret = "test_sk_x";

        String header = TossPaymentsAdapter.buildBasicAuthHeader(secret);

        String decoded = new String(
                Base64.getDecoder().decode(header.substring("Basic ".length())),
                StandardCharsets.UTF_8);
        assertThat(decoded).endsWith(":");
        assertThat(decoded).isNotEqualTo(secret); // 콜론 누락 시 secret 과 동일했을 것
    }
}
