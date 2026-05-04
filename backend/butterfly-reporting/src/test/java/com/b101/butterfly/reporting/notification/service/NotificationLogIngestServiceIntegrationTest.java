package com.b101.butterfly.reporting.notification.service;

import com.b101.butterfly.reporting.notification.dto.NotificationLogIngestRequest;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationLogIngestService의 멱등 적재 동작을 실제 DB(Testcontainers)에서 검증한다.
 * S14P31B101-176 인수 조건 — "같은 messageKey 재호출 시 동일 logId" 검증.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationLogIngestServiceIntegrationTest extends AbstractMySqlContainerTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 4, 28, 10, 0, 5);

    @Autowired private NotificationLogIngestService service;
    @Autowired private NotificationLogRepository repository;

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
