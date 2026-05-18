package com.b101.butterfly.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 결제 모듈 설정 — {@link TossPaymentsProperties} 바인딩만 활성화.
 *
 * <p>{@code RestClient.Builder} 는 Spring Boot 의 {@code RestClientAutoConfiguration} 이 자동 제공한다.
 */
@Configuration
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class PaymentConfig {
}
