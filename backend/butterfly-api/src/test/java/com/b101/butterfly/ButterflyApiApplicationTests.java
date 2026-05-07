package com.b101.butterfly;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 빈 컨텍스트 로딩 sanity 테스트.
 *
 * <p>main application.properties는 자격증명을 default 없는 {@code ${ENV_VAR}} placeholder로
 * 둔다 (운영 보안 정책). 본 테스트에서는 .env 또는 시스템 환경변수에 의존하지 않도록
 * 테스트 전용 default를 {@link TestPropertySource}로 주입한다.
 *
 * <p>Flyway baseline 마이그레이션 적용을 위해 TestContainers MySQL 8 인스턴스를 띄우고
 * {@link ServiceConnection}으로 spring.datasource.* 프로퍼티를 자동 바인딩한다.
 * (CI integration-test 잡에서 docker:dind 기반으로 동작. 단위 잡에서는 @Tag("integration")으로 제외.)
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
		"spring.rabbitmq.username=guest",
		"spring.rabbitmq.password=guest",
		"jwt.secret=butterfly-test-secret-key-must-be-32chars!!",
		"aes.secret-key=0123456789abcdef0123456789abcdef",
		"mail.from=no-reply@test.local",
		"spring.mail.username=test@test.local",
		"spring.mail.password=testpassword"
})
class ButterflyApiApplicationTests {

	@Container
	@ServiceConnection
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("butterfly_test")
			.withUsername("test")
			.withPassword("test")
			// binary logging이 활성화된 MySQL 8에서 non-SUPER 사용자도 트리거를 생성할 수 있도록 허용 (V3 audit_triggers)
			.withCommand("--log_bin_trust_function_creators=1");

	@Test
	void contextLoads() {
	}

}
