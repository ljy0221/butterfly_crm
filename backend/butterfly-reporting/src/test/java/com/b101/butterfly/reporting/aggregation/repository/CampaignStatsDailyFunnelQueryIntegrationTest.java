package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-90 — 펀널 합산 쿼리 (aggregateFunnel) JPQL 검증.
 *
 * <p>고유 projectId/campaignId로 다른 통합 테스트와 격리. JdbcTemplate으로 직접 INSERT하여
 * native UPSERT의 ON DUPLICATE KEY 영향을 배제.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CampaignStatsDailyFunnelQueryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired
    private CampaignStatsDailyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long PROJECT_ID = 90_001L;
    private static final long CAMPAIGN_A = 90_100L;
    private static final long CAMPAIGN_B = 90_101L;

    @BeforeEach
    void insertParentFixtures() {
        // 1. member (owner of projects)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO members (member_id, project_id, email, name, phone, birth_date, role_type, member_status)
                VALUES (1, NULL, 'funnel-test@test.com', '펀넬테스트', '01012345678', '1990-01-01', 'OWNER', 'ACTIVE')
                """);
        // 2. project (project_id=90001 used as PROJECT_ID)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO projects (project_id, name, business_no, company_auth_no, owner_id, plan)
                VALUES (90001, 'funnel-project', '123-45-90001', 'COMP9001', 1, 'FREE')
                """);
        // 3. message_template (required by campaigns)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO message_templates
                    (message_template_id, member_id, project_id, message_template_name,
                     message_template_description, channel, message_template_title,
                     message_template_body, required_variables, is_deleted)
                VALUES (1, 1, 90001, 'funnel-tpl', 'funnel-tpl-desc', 'PUSH', 'title', 'body', '[]', false)
                """);
        // 4. campaigns (campaign_id=90100, 90101 used as CAMPAIGN_A, CAMPAIGN_B)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO campaigns
                    (campaign_id, project_id, message_template_id, segment_id, member_id,
                     campaign_name, campaign_description, campaign_type, channel,
                     campaign_status, send_mode, is_deleted)
                VALUES (90100, 90001, 1, NULL, 1, 'campaign-a', 'desc', 'ONE_TIME', 'PUSH', 'DRAFT', 'IMMEDIATE', false)
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO campaigns
                    (campaign_id, project_id, message_template_id, segment_id, member_id,
                     campaign_name, campaign_description, campaign_type, channel,
                     campaign_status, send_mode, is_deleted)
                VALUES (90101, 90001, 1, NULL, 1, 'campaign-b', 'desc', 'ONE_TIME', 'EMAIL', 'DRAFT', 'IMMEDIATE', false)
                """);
        // clean isolated range before test
        jdbcTemplate.update(
                "DELETE FROM campaign_stats_daily WHERE project_id = ?", PROJECT_ID);
    }

    @AfterEach
    void cleanIsolatedRange() {
        jdbcTemplate.update(
                "DELETE FROM campaign_stats_daily WHERE project_id = ?", PROJECT_ID);
    }

    private void insertRow(long campaignId, Channel channel, LocalDate bucketDate,
                           int sent, int delivered, int open, int click, int convert) {
        jdbcTemplate.update(
                "INSERT INTO campaign_stats_daily "
                        + "(project_id, campaign_id, channel, bucket_date, "
                        + " sent_count, delivered_count, failed_count, "
                        + " open_count, click_count, convert_count, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                PROJECT_ID, campaignId, channel.name(), bucketDate,
                sent, delivered, 0, open, click, convert);
    }

    @Test
    @DisplayName("모든 캠페인/채널 합산 — 기간 내 sent/delivered/open/click/convert 합산")
    void aggregatesAllCampaignsAndChannels() {
        insertRow(CAMPAIGN_A, Channel.PUSH, LocalDate.of(2026, 4, 1), 100, 80, 40, 10, 5);
        insertRow(CAMPAIGN_A, Channel.PUSH, LocalDate.of(2026, 4, 2), 200, 180, 100, 30, 15);
        insertRow(CAMPAIGN_B, Channel.EMAIL, LocalDate.of(2026, 4, 3), 50, 50, 25, 5, 2);
        // 기간 밖 — 제외
        insertRow(CAMPAIGN_A, Channel.PUSH, LocalDate.of(2026, 5, 1), 999, 999, 999, 999, 999);

        FunnelCountRow row = repository.aggregateFunnel(
                PROJECT_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, null);

        assertThat(row.sentCount()).isEqualTo(350);
        assertThat(row.deliveredCount()).isEqualTo(310);
        assertThat(row.openCount()).isEqualTo(165);
        assertThat(row.clickCount()).isEqualTo(45);
        assertThat(row.convertCount()).isEqualTo(22);
    }

    @Test
    @DisplayName("campaignId 필터 — 단일 캠페인만 합산")
    void filtersBySingleCampaign() {
        insertRow(CAMPAIGN_A, Channel.PUSH, LocalDate.of(2026, 4, 1), 100, 80, 40, 10, 5);
        insertRow(CAMPAIGN_B, Channel.PUSH, LocalDate.of(2026, 4, 1), 999, 999, 999, 999, 999);

        FunnelCountRow row = repository.aggregateFunnel(
                PROJECT_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), CAMPAIGN_A, null);

        assertThat(row.sentCount()).isEqualTo(100);
        assertThat(row.deliveredCount()).isEqualTo(80);
    }

    @Test
    @DisplayName("channel 필터 — 단일 채널만 합산")
    void filtersBySingleChannel() {
        insertRow(CAMPAIGN_A, Channel.PUSH, LocalDate.of(2026, 4, 1), 100, 80, 40, 10, 5);
        insertRow(CAMPAIGN_A, Channel.EMAIL, LocalDate.of(2026, 4, 2), 999, 999, 999, 999, 999);

        FunnelCountRow row = repository.aggregateFunnel(
                PROJECT_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, Channel.PUSH);

        assertThat(row.sentCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("매칭 row 0건 — COALESCE로 0L 반환 (NPE 방지)")
    void returnsZeroWhenNoMatch() {
        FunnelCountRow row = repository.aggregateFunnel(
                999_999L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, null);

        assertThat(row.sentCount()).isZero();
        assertThat(row.deliveredCount()).isZero();
        assertThat(row.openCount()).isZero();
        assertThat(row.clickCount()).isZero();
        assertThat(row.convertCount()).isZero();
    }
}
