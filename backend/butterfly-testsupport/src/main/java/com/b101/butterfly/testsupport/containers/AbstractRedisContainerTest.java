package com.b101.butterfly.testsupport.containers;

import com.b101.butterfly.testsupport.IntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 통합 테스트 공통 베이스. 단일 정적 컨테이너를 JVM 수명 동안 재사용한다.
 */
@IntegrationTest
public abstract class AbstractRedisContainerTest {

    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT)
                    .withReuse(true);

    static {
        CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> CONTAINER.getMappedPort(REDIS_PORT));
    }
}
