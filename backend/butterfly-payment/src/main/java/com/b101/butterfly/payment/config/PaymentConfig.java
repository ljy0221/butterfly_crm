package com.b101.butterfly.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 결제 모듈 설정.
 * {@link TossPaymentsProperties} 활성화 + 어댑터가 사용할 {@link RestClient.Builder} bean 보장.
 */
@Configuration
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class PaymentConfig {

    @Bean
    public RestClient.Builder paymentRestClientBuilder() {
        return RestClient.builder();
    }
}
