package com.b101.butterfly.reporting.audit.repository;

import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.b101.butterfly.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * audit_logs append-only 트리거 검증 (S14P31B101-125 §"통합 검증 시나리오").
 *
 * <p>JPA {@code @Immutable}은 UPDATE 쿼리 발행 자체를 차단하므로, 트리거의 실제 동작은
 * JdbcTemplate으로 raw SQL을 직접 실행해 검증한다.
 *
 * <p>DB 권한 분리(GRANT) 검증은 별도 티켓 S14P31B101-173에서 다룬다.
 */
@IntegrationTest
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/db/test/audit_logs_triggers.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AuditLogTriggerIntegrationTest {

    private static final String SQLSTATE_45000 = "45000";

    // 트리거 CREATE는 SUPER 권한 또는 log_bin_trust_function_creators=1 이 필요해
    // 컨테이너 명령줄에서 직접 켠다. testsupport의 공용 컨테이너는 트리거를 쓰지 않으므로
    // 본 테스트만 별도 컨테이너를 띄운다.
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>("mysql:8.0")
            .withCommand("--log-bin-trust-function-creators=1");

    static {
        CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
    }

    @Autowired private AuditLogRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long insertSampleAuditLog() {
        AuditLog saved = repository.save(AuditLog.create(
                100L,
                7L,
                "FCM_KEY_ROTATE",
                "PROJECT",
                100L,
                Map.of("hash", "abc123"),
                "10.0.0.1",
                "JUnit/Test"
        ));
        entityManager.flush();
        entityManager.clear();
        return saved.getId();
    }

    @Test
    @DisplayName("INSERT는 정상 동작하고 SELECT로 조회된다")
    void insertAndSelectWorks() {
        Long id = insertSampleAuditLog();

        AuditLog found = repository.findById(id).orElseThrow();
        assertThat(found.getAction()).isEqualTo("FCM_KEY_ROTATE");
        assertThat(found.getTargetType()).isEqualTo("PROJECT");
    }

    @Test
    @DisplayName("UPDATE 시도 시 트리거가 SQLSTATE 45000으로 차단한다 (append-only)")
    void updateBlockedByTrigger() {
        Long id = insertSampleAuditLog();

        assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE audit_logs SET action = ? WHERE audit_id = ?", "TAMPERED", id)
        )
                .isInstanceOf(DataAccessException.class)
                .satisfies(t -> assertThat(rootSqlState(t)).isEqualTo(SQLSTATE_45000))
                .hasMessageContaining("audit_logs is append-only")
                .hasMessageContaining("UPDATE is forbidden");

        AuditLog after = repository.findById(id).orElseThrow();
        assertThat(after.getAction())
                .as("UPDATE가 차단되었으므로 원래 값이 보존된다")
                .isEqualTo("FCM_KEY_ROTATE");
    }

    @Test
    @DisplayName("DELETE 시도 시 트리거가 SQLSTATE 45000으로 차단한다 (append-only)")
    void deleteBlockedByTrigger() {
        Long id = insertSampleAuditLog();

        assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM audit_logs WHERE audit_id = ?", id)
        )
                .isInstanceOf(DataAccessException.class)
                .satisfies(t -> assertThat(rootSqlState(t)).isEqualTo(SQLSTATE_45000))
                .hasMessageContaining("audit_logs is append-only")
                .hasMessageContaining("DELETE is forbidden");

        assertThat(repository.findById(id))
                .as("DELETE가 차단되었으므로 행이 그대로 남아 있다")
                .isPresent();
    }

    private static String rootSqlState(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SQLException sql) {
                return sql.getSQLState();
            }
            cur = cur.getCause();
        }
        return null;
    }
}
