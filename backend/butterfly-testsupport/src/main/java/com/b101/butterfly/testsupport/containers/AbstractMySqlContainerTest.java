package com.b101.butterfly.testsupport.containers;

import com.b101.butterfly.testsupport.IntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Repository 통합 테스트 공통 베이스. 단일 정적 MySQLContainer를 JVM 수명 동안 재사용해
 * 클래스마다 컨테이너를 다시 띄우는 비용을 피한다.
 *
 * <p>{@code --log_bin_trust_function_creators=1} 옵션은 V3 audit_triggers 마이그레이션이
 * non-SUPER 사용자로 트리거를 생성할 수 있도록 허용. 트리거를 사용하지 않는 테스트에도 무해.
 *
 * <p>컨테이너 인스턴스가 직접 필요한 서브클래스(예: information_schema 쿼리에
 * databaseName 필요)는 {@link #CONTAINER} 정적 필드를 protected로 접근.
 */
@IntegrationTest
public abstract class AbstractMySqlContainerTest {

    protected static final MySQLContainer<?> CONTAINER =
            new MySQLContainer<>("mysql:8.0")
                    .withCommand("--log_bin_trust_function_creators=1");

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
