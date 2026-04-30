package com.b101.butterfly.testsupport.containers;

import com.b101.butterfly.testsupport.IntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kafka 통합 테스트 공통 베이스. 단일 정적 컨테이너를 JVM 수명 동안 재사용한다.
 */
@IntegrationTest
public abstract class AbstractKafkaContainerTest {

    private static final KafkaContainer CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    static {
        CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", CONTAINER::getBootstrapServers);
    }
}
