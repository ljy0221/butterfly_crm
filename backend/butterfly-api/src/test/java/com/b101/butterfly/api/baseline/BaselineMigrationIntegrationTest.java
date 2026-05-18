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
 * 베이스라인 V1~V20 마이그레이션이 클린 MySQL 8 testcontainer에서 단독 적용되는지 검증한다.
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
 * - V11: campaign_executions.send_completed_at NULL 허용
 * - V12: campaign_executions.send_started_at NULL 허용
 * - V13: members.member_status 컬럼 추가 (PENDING/ACTIVE)
 * - V14: message_templates 의 legacy plural 컬럼 5개 drop (S14P31B101-254) — 도메인 매핑(singular) 정렬
 * - V15: campaign_executions 의 결과 통계 3개 컬럼 (sent_count/failed_count/fail_reason) drop (S14P31B101-282)
 * - V17: segment_members 변수 스냅샷 컬럼 추가 + segment_variable_stats 테이블 생성
 * - V18: segment_variable_snapshot_stage 테이블 생성
 * - V19: payment_order / credit_account / credit_transaction 테이블 생성
 * - V20: credit_account 예약 잔액 컬럼 + credit_reservation 테이블 생성 + credit_transaction reference 컬럼 추가
 * - flyway_schema_history: 19 row
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
    void v1_v20_적용_후_테이블_37개_생성() {
        // V9: token_blacklist DROP → 32개 → 31개.
        // V17/V18 변수 스냅샷 테이블 2개, V19 결제 테이블 3개, V20 예약 테이블 1개를 추가한다.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "AND table_name <> 'flyway_schema_history'",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(37);
    }

    @Test
    void schema_history_19개_row_성공_상태() {
        // V1 ~ V15, V17 ~ V20
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        assertThat(count).isEqualTo(19);
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
    void v13_members_member_status_컬럼_추가() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'members' " +
                "AND column_name = 'member_status'",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v14_message_templates_legacy_plural_컬럼_제거() {
        // V1 baseline 이 둔 plural 5개 (S14P31B101-254). singular 매핑(JPA 엔티티) 과 정렬.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'message_templates' " +
                "AND column_name IN ('message_templates_name', 'message_templates_description', " +
                "'message_templates_title', 'message_templates_body', 'message_templates_image_url')",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(0);
    }

    @Test
    void v15_campaign_executions_결과_통계_컬럼_제거() {
        // V1 baseline 이 둔 sent_count/failed_count/fail_reason 3개 (S14P31B101-282).
        // worker→campaign 결과 통보 미구현으로 placeholder 였던 컬럼 — 도메인 정합성 정리.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'campaign_executions' " +
                "AND column_name IN ('sent_count', 'failed_count', 'fail_reason')",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(0);
    }

    @Test
    void v19_결제_크레딧_테이블_3개_생성() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "AND table_name IN ('payment_order', 'credit_account', 'credit_transaction')",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(count).isEqualTo(3);
    }

    @Test
    void v20_크레딧_예약_스키마_추가() {
        Integer reservationTableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "AND table_name = 'credit_reservation'",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(reservationTableCount).isEqualTo(1);

        Integer accountReservedColumnCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'credit_account' " +
                "AND column_name = 'reserved_balance'",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(accountReservedColumnCount).isEqualTo(1);

        Integer transactionReferenceColumnCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = 'credit_transaction' " +
                "AND column_name IN ('reference_type', 'reference_id')",
                Integer.class, CONTAINER.getDatabaseName());
        assertThat(transactionReferenceColumnCount).isEqualTo(2);
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
