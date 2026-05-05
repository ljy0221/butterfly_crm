package com.b101.butterfly.reporting.audit.permission;

import com.b101.butterfly.testsupport.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S14P31B101-183 (단계 0) — `app_user` 계정의 audit_logs 권한 부족이 트리거 도달 전에
 * access denied로 차단되는지 CI에서 자동 검증한다.
 *
 * <p>본 테스트는 운영 db/grants/app_user.sql 정의를 같은 의미로 옮긴
 * {@code /db/test/audit_db_permission_test_init.sql}을 Testcontainers MySQL에 init
 * script로 적용한 뒤, root와 app_user 두 connection으로 다음을 검증한다:
 *
 * <ul>
 *   <li>app_user → audit_logs INSERT 정상</li>
 *   <li>app_user → audit_logs SELECT 정상</li>
 *   <li>app_user → audit_logs UPDATE 시도 → access denied (SQLSTATE 42000, error 1142)</li>
 *   <li>app_user → audit_logs DELETE 시도 → access denied (SQLSTATE 42000, error 1142)</li>
 *   <li>app_user → notification_logs UPDATE 정상 (대조군)</li>
 *   <li>root → audit_logs UPDATE 시도 → 트리거가 차단 (SQLSTATE 45000) — 권한이 있어도 트리거가 막는지 확인</li>
 * </ul>
 *
 * <p>access denied가 트리거(SQLSTATE 45000)보다 먼저 발생하는지가 본 테스트의 핵심 — GRANT 측
 * 방어선이 트리거 도달 전 단계에서 동작해야 더 안전하다 (제안서 §"트리거만으로는 부족한 이유").
 */
@IntegrationTest
class AuditDbPermissionIntegrationTest {

    private static final String INIT_SCRIPT = "db/test/audit_db_permission_test_init.sql";
    private static final String APP_USER = "app_user";
    private static final String APP_USER_PASSWORD = "test-app-password";

    private static final String SQLSTATE_ACCESS_DENIED = "42000";
    private static final int MYSQL_ER_TABLEACCESS_DENIED_ERROR = 1142;

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>("mysql:8.0")
            // 트리거 CREATE는 binary log 활성 환경에서 SUPER 권한이 필요하므로 컨테이너
            // 차원에서 log_bin_trust_function_creators=1을 켠다 (S14P31B101-125 동일 패턴).
            .withCommand("--log-bin-trust-function-creators=1")
            // init script로 docker-entrypoint-initdb.d에 두면 mysql daemon이 root로 실행하므로
            // CREATE USER + GRANT가 통과한다. withInitScript는 default user로 실행돼 막힌다.
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource(INIT_SCRIPT),
                    "/docker-entrypoint-initdb.d/01-permission-test-init.sql"
            );

    @BeforeAll
    static void start() {
        CONTAINER.start();
    }

    @AfterAll
    static void stop() {
        CONTAINER.stop();
    }

    private Connection rootConnection() throws SQLException {
        return DriverManager.getConnection(
                CONTAINER.getJdbcUrl(),
                CONTAINER.getUsername(),
                CONTAINER.getPassword()
        );
    }

    private Connection appUserConnection() throws SQLException {
        // app_user는 init script로 만들어진 별도 계정. JDBC URL은 동일하게 쓰되 인증만 교체.
        return DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), APP_USER, APP_USER_PASSWORD
        );
    }

    private long insertSampleAuditLog(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO audit_logs (project_id, member_id, action, target_type, target_id) "
                        + "VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, 100L);
            ps.setLong(2, 7L);
            ps.setString(3, "FCM_KEY_ROTATE");
            ps.setString(4, "PROJECT");
            ps.setLong(5, 100L);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("app_user는 audit_logs INSERT/SELECT가 정상 동작한다")
    void appUserCanInsertAndSelectAuditLogs() throws SQLException {
        try (Connection conn = appUserConnection()) {
            long id = insertSampleAuditLog(conn);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT action FROM audit_logs WHERE audit_id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("FCM_KEY_ROTATE");
                }
            }
        }
    }

    @Test
    @DisplayName("app_user의 audit_logs UPDATE 시도는 access denied로 차단된다 (트리거 도달 전)")
    void appUserAuditLogsUpdateAccessDenied() throws SQLException {
        long id;
        try (Connection conn = appUserConnection()) {
            id = insertSampleAuditLog(conn);
        }

        assertThatThrownBy(() -> {
            try (Connection conn = appUserConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE audit_logs SET action = 'TAMPERED' WHERE audit_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        })
                .isInstanceOfSatisfying(SQLException.class, ex -> {
                    assertThat(ex.getSQLState())
                            .as("GRANT 부족이 트리거 도달 전에 access denied (42000)로 잡혀야 한다")
                            .isEqualTo(SQLSTATE_ACCESS_DENIED);
                    assertThat(ex.getErrorCode()).isEqualTo(MYSQL_ER_TABLEACCESS_DENIED_ERROR);
                });
    }

    @Test
    @DisplayName("app_user의 audit_logs DELETE 시도는 access denied로 차단된다 (트리거 도달 전)")
    void appUserAuditLogsDeleteAccessDenied() throws SQLException {
        long id;
        try (Connection conn = appUserConnection()) {
            id = insertSampleAuditLog(conn);
        }

        assertThatThrownBy(() -> {
            try (Connection conn = appUserConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM audit_logs WHERE audit_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        })
                .isInstanceOfSatisfying(SQLException.class, ex -> {
                    assertThat(ex.getSQLState()).isEqualTo(SQLSTATE_ACCESS_DENIED);
                    assertThat(ex.getErrorCode()).isEqualTo(MYSQL_ER_TABLEACCESS_DENIED_ERROR);
                });
    }

    @Test
    @DisplayName("app_user는 대조군 notification_logs에 INSERT/UPDATE가 정상 동작한다")
    void appUserCanUpdateNotificationLogs() throws SQLException {
        try (Connection conn = appUserConnection()) {
            long id;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO notification_logs (project_id, campaign_id, channel, status) "
                            + "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, 1L);
                ps.setLong(2, 1L);
                ps.setString(3, "PUSH");
                ps.setString(4, "PENDING");
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    assertThat(keys.next()).isTrue();
                    id = keys.getLong(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE notification_logs SET status = 'SENT' WHERE log_id = ?")) {
                ps.setLong(1, id);
                int updated = ps.executeUpdate();
                assertThat(updated).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("root는 audit_logs UPDATE 시도 시 트리거가 SQLSTATE 45000으로 차단한다 (권한이 있어도)")
    void rootAuditLogsUpdateBlockedByTrigger() throws SQLException {
        long id;
        try (Connection conn = rootConnection()) {
            id = insertSampleAuditLog(conn);
        }

        assertThatThrownBy(() -> {
            try (Connection conn = rootConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE audit_logs SET action = 'TAMPERED' WHERE audit_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        })
                .isInstanceOfSatisfying(SQLException.class, ex ->
                        assertThat(ex.getSQLState())
                                .as("권한이 있는 root도 트리거에 의해 막혀야 한다")
                                .isEqualTo("45000"));
    }
}
