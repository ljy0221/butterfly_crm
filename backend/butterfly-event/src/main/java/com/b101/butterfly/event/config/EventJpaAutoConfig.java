package com.b101.butterfly.event.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** butterfly-event 모듈의 JPA 스캔 범위를 자기 패키지로 한정한다. */
@AutoConfiguration
@EntityScan("com.b101.butterfly.event")
@EnableJpaRepositories("com.b101.butterfly.event")
public class EventJpaAutoConfig {
}
