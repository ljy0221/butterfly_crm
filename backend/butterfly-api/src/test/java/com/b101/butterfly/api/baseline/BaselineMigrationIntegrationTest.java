package com.b101.butterfly.api.baseline;

import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 베이스라인 V1~V10 마이그레이션이 클린 MySQL 8 testcontainer에서 단독 적용되는지 검증한다.
 *
 * - V1: 22개 테이블 (core_baseline)
 * - V2: 10개 테이블 (reporting_baseline)
 * - V3: audit_logs 트리거 2개
 * - V4: segments 테이블에 build_token / build_started_at / build_heartbeat_at 추가 + schema_version DEFAULT 4 (S14P31B101-181)
 * - V5: projects.fcm_credentials drop (S14P31B101-188)
 * - V6: campaign_executions.version (optimistic lock)
 * - V7: devices.external_id 추가 (ADR-0012, S14P31B101-21)
 * - V8: 세그먼트/이벤트 조회용 인덱스 2개 (S14P31B101-195)
 * - V9: token_blacklist 테이블 DROP (Redis 블랙리스트로 대체)
 * - V10: event_schema_fields.field_type ENUM 에 OBJECT 추가 (S14P31B101-225)
 * - flyway_schema_history: 10 row
 *
 * butterfly-api의 메인 ButterflyApiApplication 대신 별도 TestConfig를 사용해
 * 다른 도메인 모듈의 @EnableJpaRepositories 자동 스캔을 격리한다.
 * DataSource + Flyway + JdbcTemplate만 구성하며 JPA 자동설정은 로드하지 않는다.
 *
 * MySQL 컨테이너는 testsupport의 {@link AbstractMySqlContainerTest}를 상속하여
 * JVM 수명 동안 단일 인스턴스를 다른 통합 테스트와 공유한다 (메모리 압박 완화 + 부팅 1회).
 * databaseName이 필요한 information_schema 쿼리는 {@link #CONTAINER}.getDatabaseName()으로 접근.
 */
@SpringBootTest(
        classes = {
                DataSourceAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class
        },
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
)
class BaselineMigrationIntegrationTest extends AbstractMySqlContainerTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1_v10_적용_후_테이블_31개_생성() {
        // V9: token_blacklist DROP → 32개 → 31개. V10 은 enum 확장만이라 테이블 수 변화 없음.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "AND table_name <> 'flyway_schema_history'",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(31);
    }

    @Test
    void schema_history_10개_row_성공_상태() {
        // V1 ~ V10 — V10(event_schema_fields.field_type OBJECT enum 확장) 추가됨.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        assertThat(count).isEqualTo(10);
    }

    @Test
    void v10_event_schema_fields_field_type_OBJECT_포함() {
        // V10 으로 ENUM 에 OBJECT 가 추가됐는지 검증.
        String columnType = jdbc.queryForObject(
                "SELECT column_type FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'event_schema_fields' " +
                "AND column_name = 'field_type'",
                String.class, CONTAINER.getDatabaseName());
        assertThat(columnType).contains("'OBJECT'");
    }

    @Test
    void audit_logs_트리거_2개_등록() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.triggers " +
                "WHERE trigger_schema = ? AND event_object_table = 'audit_logs'",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(2);
    }

    @Test
    void audit_logs_UPDATE_차단() {
        jdbc.update("INSERT INTO audit_logs (member_id, action, target_type, created_at) " +
                    "VALUES (1, 'TEST', 'test', NOW())");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action='HACK' WHERE audit_id=1"))
                .hasMessageContaining("audit_logs is append-only");
    }

    @Test
    void v4_segments_빌드락_컬럼_3개_추가() {
        // build_token / build_started_at / build_heartbeat_at 컬럼 존재 검증
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'segments' " +
                "AND column_name IN ('build_token', 'build_started_at', 'build_heartbeat_at')",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(3);
    }

    @Test
    void v4_segments_schema_version_default_4() {
        String columnDefault = jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'segments' AND column_name = 'schema_version'",
                String.class, CONTAINER.getDatabaseName());
        assertThat(columnDefault).isEqualTo("4");
    }
}
