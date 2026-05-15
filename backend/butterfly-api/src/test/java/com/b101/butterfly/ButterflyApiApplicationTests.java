package com.b101.butterfly;

import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 빈 컨텍스트 로딩 sanity 테스트.
 *
 * <p>main application.properties는 자격증명을 default 없는 {@code ${ENV_VAR}} placeholder로
 * 둔다 (운영 보안 정책). 본 테스트에서는 .env 또는 시스템 환경변수에 의존하지 않도록
 * 테스트 전용 default를 {@link TestPropertySource}로 주입한다.
 *
 * <p>Flyway baseline 마이그레이션 적용을 위해 testsupport의 MySQL 8 컨테이너를
 * JVM 수명 동안 다른 통합 테스트와 공유한다 (메모리 압박 완화).
 */
@SpringBootTest
@TestPropertySource(properties = {
		"jwt.secret=butterfly-test-secret-key-must-be-32chars!!",
		"mail.from=no-reply@test.local",
		"spring.mail.username=test@test.local",
		"spring.mail.password=testpassword",
		// 통합 테스트는 Flyway가 만든 스키마만 사용. Hibernate ddl-auto=update가 운영 엔티티
		// 기준으로 추가 테이블을 만들면 컨테이너 공유 중인 BaselineMigrationIntegrationTest의
		// 테이블 카운트(32개)가 어긋나므로 명시적으로 none으로 격리.
		"spring.jpa.hibernate.ddl-auto=none",
		// Spring AI 1.1.x OpenAI starter 통합 테스트 컨텍스트 로딩 fix (S14P31B101-295).
		// (1) ChatClient.Builder auto-config 는 ChatModel 빈 의존인데 application.yml 의
		//     spring.ai.model.chat=none 으로 ChatModel 이 안 만들어지므로 ChatClient 자체 차단.
		// (2) OpenAiAudioSpeechAutoConfiguration 등 chat 이외 sub-model 들은 ${SPRING_AI_MODEL_*}
		//     로 막혀있지 않아 빈 api-key 로 IllegalArgumentException. dummy key 로 빈 생성만 통과.
		//     실제 호출은 SPRING_AI_MODEL_CHAT=none 이라 일어나지 않음 + AiCopilotService 는
		//     ObjectProvider 패턴이라 ChatClient.Builder 빈 없으면 자체 fallback.
		"spring.ai.chat.client.enabled=false",
		"spring.ai.openai.api-key=sk-test-dummy-not-used"
})
class ButterflyApiApplicationTests extends AbstractMySqlContainerTest {

	@Test
	void contextLoads() {
	}

}
