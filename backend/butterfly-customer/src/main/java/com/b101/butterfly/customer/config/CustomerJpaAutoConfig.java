package com.b101.butterfly.customer.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** butterfly-customer 모듈의 JPA 스캔 범위를 자기 패키지로 한정한다. */
@AutoConfiguration
@EntityScan("com.b101.butterfly.customer")
@EnableJpaRepositories("com.b101.butterfly.customer")
public class CustomerJpaAutoConfig {
}
