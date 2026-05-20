package com.b101.butterfly.reporting.notification.service;

import com.b101.butterfly.reporting.notification.dto.NotificationLogIngestRequest;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.AbstractReportingMySqlTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationLogIngestService의 멱등 적재 동작을 실제 DB(Testcontainers)에서 검증한다.
 * S14P31B101-176 인수 조건 — "같은 messageKey 재호출 시 동일 logId" 검증.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationLogIngestServiceIntegrationTest extends AbstractReportingMySqlTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 4, 28, 10, 0, 5);

    @Autowired private NotificationLogIngestService service;
    @Autowired private ReportingNotificationLogRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertParentFixtures() {
        // 1. member (owner of project, and campaign creator)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO members (member_id, project_id, email, name, phone, birth_date, role_type, member_status)
                VALUES (1, NULL, 'ingest-test@test.com', '인제스트테스트', '01012345678', '1990-01-01', 'OWNER', 'ACTIVE')
                """);
        // 2. project (project_id=123 used in request)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO projects (project_id, name, business_no, company_auth_no, owner_id, plan)
                VALUES (123, 'ingest-project', '123-45-67890', 'COMP123', 1, 'FREE')
                """);
        // 3. message_template (required by campaigns)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO message_templates
                    (message_template_id, member_id, project_id, message_template_name,
                     message_template_description, channel, message_template_title,
                     message_template_body, required_variables, is_deleted)
                VALUES (1, 1, 123, 'ingest-tpl', 'ingest-tpl-desc', 'PUSH', 'title', 'body', '[]', false)
                """);
        // 4. campaign (campaign_id=4521 used in request)
        jdbcTemplate.execute("""
                INSERT IGNORE INTO campaigns
                    (campaign_id, project_id, message_template_id, segment_id, member_id,
                     campaign_name, campaign_description, campaign_type, channel,
                     campaign_status, send_mode, is_deleted)
                VALUES (4521, 123, 1, NULL, 1,
                        'ingest-campaign', 'desc', 'ONE_TIME', 'PUSH',
                        'DRAFT', 'IMMEDIATE', false)
                """);
    }

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    private NotificationLogIngestRequest request(String messageKey) {
        return request(messageKey, NotificationStatus.SENT);
    }

    private NotificationLogIngestRequest request(String messageKey, NotificationStatus status) {
        return new NotificationLogIngestRequest(
                messageKey, 123L, 4521L, 88123L, 89L,
                78901L, 23456L, Channel.PUSH, status, SENT_AT,
                null, null, null
        );
    }

    private NotificationLogIngestRequest fullRequest(
            String messageKey,
            NotificationStatus status,
            LocalDateTime sentAt,
            LocalDateTime deliveredAt,
            String errorCode,
            String errorMessage,
            Long campaignExecutionId,
            Long messageTemplateId
    ) {
        return new NotificationLogIngestRequest(
                messageKey, 123L, 4521L, campaignExecutionId, messageTemplateId,
                78901L, 23456L, Channel.PUSH, status, sentAt,
                deliveredAt, errorCode, errorMessage
        );
    }

    @Test
    @DisplayName("신규 messageKey → INSERT 후 deduplicated=false")
    void insertsNew() {
        NotificationLogIngestResult result = service.record(request("kafka-msg-new"));

        assertThat(result.deduplicated()).isFalse();
        assertThat(result.log().getId()).isNotNull();
        assertThat(repository.findByMessageKey("kafka-msg-new")).isPresent();
    }

    @Test
    @DisplayName("같은 messageKey 재호출 → INSERT 없이 기존 logId 반환, deduplicated=true")
    void dedupsOnRepeat() {
        NotificationLogIngestResult first = service.record(request("kafka-msg-dup"));
        NotificationLogIngestResult second = service.record(request("kafka-msg-dup"));

        assertThat(first.deduplicated()).isFalse();
        assertThat(second.deduplicated()).isTrue();
        assertThat(second.log().getId()).isEqualTo(first.log().getId());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PENDING → SENT 전이는 UPDATE 적용 (deduplicated=true, status 갱신, sentAt 채워짐)")
    void appliesValidTransition() {
        LocalDateTime initialSentAt = null;
        LocalDateTime updatedSentAt = LocalDateTime.of(2026, 4, 28, 10, 0, 30);

        service.record(fullRequest(
                "kafka-msg-trans", NotificationStatus.PENDING, initialSentAt, null,
                null, null, 88123L, 89L
        ));
        NotificationLogIngestResult after = service.record(fullRequest(
                "kafka-msg-trans", NotificationStatus.SENT, updatedSentAt, null,
                null, null, 88123L, 89L
        ));

        assertThat(after.deduplicated()).isTrue();
        assertThat(after.log().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(after.log().getSentAt()).isEqualTo(updatedSentAt);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELIVERED → FAILED 같은 잘못된 전이는 거부 (status 유지, no-op)")
    void rejectsInvalidTransition() {
        service.record(fullRequest(
                "kafka-msg-bad", NotificationStatus.DELIVERED, SENT_AT,
                LocalDateTime.of(2026, 4, 28, 10, 0, 10), null, null, 88123L, 89L
        ));
        NotificationLogIngestResult after = service.record(fullRequest(
                "kafka-msg-bad", NotificationStatus.FAILED, SENT_AT, null,
                "ERR_FAKE", "should not be applied", 88123L, 89L
        ));

        assertThat(after.deduplicated()).isTrue();
        assertThat(after.log().getStatus())
                .as("DELIVERED는 종착점 — FAILED로 전이 불가")
                .isEqualTo(NotificationStatus.DELIVERED);
        assertThat(after.log().getErrorCode())
                .as("거부된 전이는 errorCode도 갱신 안 됨")
                .isNull();
    }

    @Test
    @DisplayName("campaignExecutionId/messageTemplateId 누락 메시지도 정상 적재 (WARN 로그만)")
    void acceptsMissingOptionalFields() {
        NotificationLogIngestResult result = service.record(fullRequest(
                "kafka-msg-missing", NotificationStatus.SENT, SENT_AT, null,
                null, null, null, null
        ));

        assertThat(result.deduplicated()).isFalse();
        assertThat(result.log().getCampaignExecutionId()).isNull();
        assertThat(result.log().getMessageTemplateId()).isNull();
        assertThat(repository.count()).isEqualTo(1);
    }
}
