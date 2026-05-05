package com.b101.butterfly.reporting.notification.repository;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * notification_logs 매핑 + V13의 message_key UNIQUE INDEX 가드 검증 (S14P31B101-174).
 *
 * <p>운영 마이그레이션(V1 + V13)은 Flyway로 적용되지만 본 테스트는 ddl-auto=create-drop으로
 * 빠르게 띄운 스키마 위에서 수행한다. JPA {@code @Column(unique = true)}이 동등한 UNIQUE
 * INDEX를 만들어 멱등성 동작은 동일하게 검증된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportingNotificationLogRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 4, 28, 10, 0, 5);

    @Autowired private ReportingNotificationLogRepository repository;
    @Autowired private EntityManager entityManager;

    private NotificationLog newLog(String messageKey) {
        return NotificationLog.create()
                .projectId(123L)
                .campaignId(4521L)
                .campaignExecutionId(88123L)
                .messageTemplateId(89L)
                .userId(78901L)
                .deviceId(23456L)
                .channel(Channel.PUSH)
                .status(NotificationStatus.SENT)
                .errorCode(null)
                .errorMessage(null)
                .sentAt(SENT_AT)
                .deliveredAt(null)
                .messageKey(messageKey)
                .build();
    }

    @Test
    @DisplayName("save → findByMessageKey 로 조회 가능")
    void saveAndFindByMessageKey() {
        repository.save(newLog("kafka-msg-abc123"));
        entityManager.flush();
        entityManager.clear();

        Optional<NotificationLog> found = repository.findByMessageKey("kafka-msg-abc123");

        assertThat(found).isPresent();
        assertThat(found.get().getCampaignId()).isEqualTo(4521L);
        assertThat(found.get().getChannel()).isEqualTo(Channel.PUSH);
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.get().getCreatedAt())
                .as("@PrePersist 가 created_at 을 채운다")
                .isNotNull();
    }

    @Test
    @DisplayName("같은 message_key 재INSERT 시 UNIQUE 제약으로 차단")
    void duplicateMessageKeyBlocked() {
        repository.saveAndFlush(newLog("kafka-msg-dup"));

        assertThatThrownBy(() -> repository.saveAndFlush(newLog("kafka-msg-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("message_key=NULL 행은 여러 개 허용 (V13: NULL 다중 허용)")
    void nullMessageKeyAllowsMultipleRows() {
        repository.saveAndFlush(newLog(null));
        repository.saveAndFlush(newLog(null));
        entityManager.clear();

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByMessageKey — 미존재 키는 Optional.empty")
    void findByMessageKeyNotFound() {
        Optional<NotificationLog> found = repository.findByMessageKey("never-exists");

        assertThat(found).isEmpty();
    }
}
