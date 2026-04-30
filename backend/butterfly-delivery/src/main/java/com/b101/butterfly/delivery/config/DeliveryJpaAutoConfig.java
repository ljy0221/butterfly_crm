package com.b101.butterfly.delivery.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * butterfly-delivery 모듈의 JPA 스캔 범위를 자기 패키지로 한정한다.
 *
 * <p>P2 (모듈러 모놀리스 전환 - @EntityScan 세분화). butterfly-api/butterfly-worker가
 * 이 모듈을 의존하면 Spring Boot의 AutoConfiguration 메커니즘으로 자동 활성화된다.
 */
@AutoConfiguration
@EntityScan("com.b101.butterfly.delivery")
@EnableJpaRepositories("com.b101.butterfly.delivery")
public class DeliveryJpaAutoConfig {
}
