package com.b101.butterfly.testsupport.containers;

import com.b101.butterfly.testsupport.IntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Repository 통합 테스트 공통 베이스. 단일 정적 MySQLContainer를 JVM 수명 동안 재사용해
 * 클래스마다 컨테이너를 다시 띄우는 비용을 피한다.
 *
 * <p>{@code --log_bin_trust_function_creators=1} 옵션은 V3 audit_triggers 마이그레이션이
 * non-SUPER 사용자로 트리거를 생성할 수 있도록 허용. 트리거를 사용하지 않는 테스트에도 무해.
 *
 * <p>컨테이너 인스턴스가 직접 필요한 서브클래스(예: information_schema 쿼리에
 * databaseName 필요)는 {@link #CONTAINER} 정적 필드를 protected로 접근.
 */
@IntegrationTest
public abstract class AbstractMySqlContainerTest {

    protected static final MySQLContainer<?> CONTAINER =
            new MySQLContainer<>("mysql:8.0")
                    .withCommand("--log_bin_trust_function_creators=1")
                    .withReuse(true);

    static {
        CONTAINER.start();
        createParentFixtureTables();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
    }

    private static void createParentFixtureTables() {
        try (Connection conn = DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `projects` (
                        `project_id`      BIGINT       NOT NULL AUTO_INCREMENT,
                        `name`            VARCHAR(255) NOT NULL,
                        `business_no`     VARCHAR(50)  NOT NULL,
                        `company_auth_no` VARCHAR(100) NOT NULL,
                        `owner_id`        BIGINT       NOT NULL,
                        `public_api_key`  VARCHAR(100) NULL,
                        `daily_limit`     INT          NULL,
                        `plan`            ENUM('FREE','BASIC','PRO','ENTERPRISE') NOT NULL DEFAULT 'FREE',
                        `is_deleted`      BOOLEAN      NOT NULL DEFAULT FALSE,
                        `deleted_at`      DATETIME     NULL,
                        `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`project_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `members` (
                        `member_id`      BIGINT       NOT NULL AUTO_INCREMENT,
                        `project_id`     BIGINT       NULL,
                        `email`          VARCHAR(255) NOT NULL,
                        `password_hash`  VARCHAR(255) NULL,
                        `name`           VARCHAR(20)  NOT NULL,
                        `phone`          VARCHAR(11)  NOT NULL,
                        `birth_date`     DATE         NOT NULL,
                        `role_type`      ENUM('ADMIN','OWNER','EDITOR','VIEWER') NOT NULL DEFAULT 'VIEWER',
                        `member_status`  VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
                        `is_deleted`     BOOLEAN      NOT NULL DEFAULT FALSE,
                        `deleted_at`     DATETIME     NULL,
                        `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`member_id`),
                        UNIQUE KEY `uk_members_email` (`email`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `message_templates` (
                        `message_template_id`          BIGINT       NOT NULL AUTO_INCREMENT,
                        `member_id`                    BIGINT       NOT NULL,
                        `project_id`                   BIGINT       NOT NULL,
                        `message_template_name`        VARCHAR(255) NOT NULL,
                        `message_template_description` VARCHAR(500) NOT NULL,
                        `channel`                      ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL DEFAULT 'PUSH',
                        `message_template_title`       VARCHAR(255) NOT NULL,
                        `message_template_body`        TEXT         NOT NULL,
                        `message_template_image_url`   VARCHAR(500) NULL,
                        `required_variables`           JSON         NOT NULL,
                        `is_deleted`                   BOOLEAN      NOT NULL DEFAULT FALSE,
                        `deleted_at`                   DATETIME     NULL,
                        `created_at`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`message_template_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `campaigns` (
                        `campaign_id`                BIGINT       NOT NULL AUTO_INCREMENT,
                        `project_id`                 BIGINT       NOT NULL,
                        `message_template_id`        BIGINT       NOT NULL,
                        `segment_id`                 BIGINT       NULL,
                        `member_id`                  BIGINT       NOT NULL,
                        `campaign_name`              VARCHAR(255) NOT NULL,
                        `campaign_description`       VARCHAR(500) NOT NULL,
                        `campaign_type`              ENUM('ONE_TIME','AUTOMATION') NOT NULL DEFAULT 'ONE_TIME',
                        `channel`                    ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL DEFAULT 'PUSH',
                        `campaign_status`            ENUM('DRAFT','ACTIVE','PAUSED','ENDED') NOT NULL DEFAULT 'DRAFT',
                        `send_mode`                  ENUM('IMMEDIATE','SCHEDULED','RECURRING') NOT NULL,
                        `recurring_cron`             VARCHAR(50)  NULL,
                        `repeat_type`                ENUM('DAILY','WEEKLY','MONTHLY') NULL,
                        `repeat_day_of_week`         VARCHAR(50)  NULL,
                        `repeat_day_of_month`        INT          NULL,
                        `repeat_time`                TIME         NULL,
                        `recurring_count`            INT          NULL,
                        `recurring_execution_count`  INT          NOT NULL DEFAULT 0,
                        `next_run_at`                DATETIME     NULL,
                        `version`                    INT          NOT NULL DEFAULT 0,
                        `is_deleted`                 BOOLEAN      NOT NULL DEFAULT FALSE,
                        `deleted_at`                 DATETIME     NULL,
                        `created_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`campaign_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);

        } catch (Exception e) {
            throw new RuntimeException("부모 픽스처 테이블 생성 실패", e);
        }
    }
}
