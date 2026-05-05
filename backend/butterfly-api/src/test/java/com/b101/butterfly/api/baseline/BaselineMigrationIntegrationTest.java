package com.b101.butterfly.api.baseline;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 베이스라인 V1/V2/V3/V4 마이그레이션이 클린 MySQL 8 testcontainer에서 단독 적용되는지 검증한다.
 *
 * - V1: 22개 테이블 (core_baseline)
 * - V2: 10개 테이블 (reporting_baseline)
 * - V3: audit_logs 트리거 2개
 * - V4: segments 테이블에 build_token / build_started_at / build_heartbeat_at 추가 + schema_version DEFAULT 4
 * - flyway_schema_history: 4 row
 *
 * butterfly-api의 메인 ButterflyApiApplication 대신 별도 TestConfig를 사용해
 * 다른 도메인 모듈의 @EnableJpaRepositories 자동 스캔을 격리한다.
 * DataSource + Flyway + JdbcTemplate만 구성하며 JPA 자동설정은 로드하지 않는다.
 */
@SpringBootTest(
        classes = {
                DataSourceAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class
        },
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
)
@Testcontainers
class BaselineMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("butterfly_test")
            .withUsername("test")
            .withPassword("test")
            // binary logging이 활성화된 MySQL 8에서 non-SUPER 사용자도 트리거를 생성할 수 있도록 허용
            .withCommand("--log_bin_trust_function_creators=1");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1_v2_v3_v4_적용_후_테이블_32개_생성() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "AND table_name <> 'flyway_schema_history'",
                Integer.class, mysql.getDatabaseName());
        assertThat(count).isEqualTo(32);
    }

    @Test
    void schema_history_6개_row_성공_상태() {
        // V1 ~ V6 — V5(projects.fcm_credentials drop), V6(campaign_executions.version) 추가됨.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        assertThat(count).isEqualTo(6);
    }

    @Test
    void audit_logs_트리거_2개_등록() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.triggers " +
                "WHERE trigger_schema = ? AND event_object_table = 'audit_logs'",
                Integer.class, mysql.getDatabaseName());
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
                Integer.class, mysql.getDatabaseName());
        assertThat(count).isEqualTo(3);
    }

    @Test
    void v4_segments_schema_version_default_4() {
        String columnDefault = jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'segments' AND column_name = 'schema_version'",
                String.class, mysql.getDatabaseName());
        assertThat(columnDefault).isEqualTo("4");
    }
}
