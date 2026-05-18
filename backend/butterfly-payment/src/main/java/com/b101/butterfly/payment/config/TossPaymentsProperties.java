package com.b101.butterfly.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 연동 설정 (ADR-0019).
 *
 * <p>{@code application.yml} 의 {@code butterfly.payment.toss.*} 키와 1:1 매핑.
 * 운영에서 {@code secretKey} 는 K8s Secret 으로 주입한다.
 */
@ConfigurationProperties(prefix = "butterfly.payment.toss")
public record TossPaymentsProperties(
        String clientKey,
        String secretKey,
        String baseUrl
) {
}
