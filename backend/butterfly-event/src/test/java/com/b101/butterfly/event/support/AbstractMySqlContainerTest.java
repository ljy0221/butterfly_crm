package com.b101.butterfly.event.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Repository 통합 테스트 공통 베이스. 단일 정적 MySQLContainer를 JVM 수명 동안 재사용해
 * 클래스마다 컨테이너를 다시 띄우는 비용을 피한다.
 */
public abstract class AbstractMySqlContainerTest {

    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>("mysql:8.0");

    static {
        CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
    }
}
