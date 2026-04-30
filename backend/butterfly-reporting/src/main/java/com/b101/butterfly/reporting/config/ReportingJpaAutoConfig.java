package com.b101.butterfly.reporting.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * butterfly-analytics 모듈의 JPA 스캔 범위를 자기 패키지로 한정한다.
 *
 * <p>P2 (모듈러 모놀리스 전환 - @EntityScan 세분화). butterfly-api/butterfly-worker가
 * 이 모듈을 의존하면 Spring Boot의 AutoConfiguration 메커니즘으로 자동 활성화된다.
 *
 * <p>현재 analytics 모듈은 entity/repository가 비어있으나, ADR-0007의 5종 집계 테이블과
 * ADR-0009 후 이전될 AuditLog가 추가될 때 자동으로 자기 패키지만 스캔된다.
 */
@AutoConfiguration
@EntityScan("com.b101.butterfly.reporting")
@EnableJpaRepositories("com.b101.butterfly.reporting")
public class ReportingJpaAutoConfig {
}
