package com.b101.butterfly.campaign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// 다른 모듈의 Entity 스캔 범위 확장
@EntityScan(basePackages = "com.b101.butterfly")
// 다른 모듈의 JPA Repository 스캔 범위 확장
@EnableJpaRepositories(basePackages = "com.b101.butterfly")
@SpringBootApplication(scanBasePackages = "com.b101.butterfly")
public class ButterflyCampaignApplication {

    public static void main(String[] args) {
        SpringApplication.run(ButterflyCampaignApplication.class, args);
    }
}

