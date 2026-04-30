package com.b101.butterfly.testsupport.containers;

import com.b101.butterfly.testsupport.IntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * RabbitMQ 통합 테스트 공통 베이스. 단일 정적 컨테이너를 JVM 수명 동안 재사용한다.
 */
@IntegrationTest
public abstract class AbstractRabbitMqContainerTest {

    private static final RabbitMQContainer CONTAINER =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"));

    static {
        CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerRabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", CONTAINER::getHost);
        registry.add("spring.rabbitmq.port", CONTAINER::getAmqpPort);
        registry.add("spring.rabbitmq.username", CONTAINER::getAdminUsername);
        registry.add("spring.rabbitmq.password", CONTAINER::getAdminPassword);
    }
}
