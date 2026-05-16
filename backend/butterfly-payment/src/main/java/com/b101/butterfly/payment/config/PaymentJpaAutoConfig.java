package com.b101.butterfly.payment.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * payment 모듈 JPA 엔티티/리포지토리 스캔 경계.
 */
@AutoConfiguration
@EntityScan("com.b101.butterfly.payment")
@EnableJpaRepositories("com.b101.butterfly.payment")
public class PaymentJpaAutoConfig {
}
