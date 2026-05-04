package com.b101.butterfly.reporting.notification.repository;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S14P31B101-126 정합성 검증용 카운트 쿼리(`countsForReconciliation`) 동작 슬라이스 테스트.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationCountSummaryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private ReportingNotificationLogRepository repository;

    private NotificationLog row(Long executionId, Channel channel, NotificationStatus status, String key) {
        return NotificationLog.create()
                .projectId(123L)
                .campaignId(4521L)
                .campaignExecutionId(executionId)
                .channel(channel)
                .status(status)
                .messageKey(key)
                .sentAt(LocalDateTime.of(2026, 4, 28, 10, 0))
                .build();
    }

    @Test
    @DisplayName("회차×채널 단위로 actual/sent/failed 카운트 합산")
    void countsActualSentFailed() {
        // execution=10, PUSH: SENT 2 + DELIVERED 1 + FAILED 1 + PENDING 1 → actual=5, sent=3, failed=1
        repository.save(row(10L, Channel.PUSH, NotificationStatus.SENT, "k1"));
        repository.save(row(10L, Channel.PUSH, NotificationStatus.SENT, "k2"));
        repository.save(row(10L, Channel.PUSH, NotificationStatus.DELIVERED, "k3"));
        repository.save(row(10L, Channel.PUSH, NotificationStatus.FAILED, "k4"));
        repository.save(row(10L, Channel.PUSH, NotificationStatus.PENDING, "k5"));
        // 다른 회차 — 본 카운트에 들어가면 안 됨
        repository.save(row(11L, Channel.PUSH, NotificationStatus.SENT, "k6"));
        // 같은 회차 다른 채널
        repository.save(row(10L, Channel.EMAIL, NotificationStatus.SENT, "k7"));

        NotificationCountSummary summary = repository.countsForReconciliation(10L, Channel.PUSH);

        assertThat(summary.actualAsInt()).isEqualTo(5);
        assertThat(summary.sentAsInt()).isEqualTo(3);
        assertThat(summary.failedAsInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("매칭 row 0건이어도 NPE 없이 0/0/0 반환")
    void zeroMatchReturnsZeros() {
        NotificationCountSummary summary = repository.countsForReconciliation(999L, Channel.SMS);

        assertThat(summary.actualAsInt()).isZero();
        assertThat(summary.sentAsInt()).isZero();
        assertThat(summary.failedAsInt()).isZero();
    }
}
