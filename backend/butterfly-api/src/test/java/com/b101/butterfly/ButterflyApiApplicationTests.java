package com.b101.butterfly;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 빈 컨텍스트 로딩 sanity 테스트.
 *
 * <p>main application.properties는 자격증명을 default 없는 {@code ${ENV_VAR}} placeholder로
 * 둔다 (운영 보안 정책). 본 테스트에서는 .env 또는 시스템 환경변수에 의존하지 않도록
 * 테스트 전용 default를 {@link TestPropertySource}로 주입한다.
 */
@Tag("integration")
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.username=root",
		"spring.datasource.password=root",
		"spring.rabbitmq.username=guest",
		"spring.rabbitmq.password=guest",
		"jwt.secret=butterfly-test-secret-key-must-be-32chars!!",
		"aes.secret-key=0123456789abcdef0123456789abcdef",
		"mail.from=no-reply@test.local",
		"spring.mail.username=test@test.local",
		"spring.mail.password=testpassword"
})
class ButterflyApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
