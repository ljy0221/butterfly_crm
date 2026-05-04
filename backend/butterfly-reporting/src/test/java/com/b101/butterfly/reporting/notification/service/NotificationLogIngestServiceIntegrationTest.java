package com.b101.butterfly.reporting.notification.service;

import com.b101.butterfly.reporting.notification.dto.NotificationLogIngestRequest;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.NotificationLogRepository;
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
        return new NotificationLogIngestRequest(
                messageKey, 123L, 4521L, 88123L, 89L,
                78901L, 23456L, Channel.PUSH, NotificationStatus.SENT, SENT_AT
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
}
