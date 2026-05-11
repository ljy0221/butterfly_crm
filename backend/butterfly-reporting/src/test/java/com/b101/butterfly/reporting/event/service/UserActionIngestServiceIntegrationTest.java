package com.b101.butterfly.reporting.event.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.reporting.event.dto.UserActionIngestRequest;
import com.b101.butterfly.reporting.event.entity.ActionType;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import com.b101.butterfly.reporting.event.repository.UserActionLogRepository;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S14P31B101-123 (123-A) UserActionIngestService 통합 테스트.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserActionIngestServiceIntegrationTest extends AbstractMySqlContainerTest {

    private static final LocalDateTime ACTION_AT = LocalDateTime.of(2026, 4, 28, 10, 2, 32);

    @Autowired private UserActionIngestService service;
    @Autowired private UserActionLogRepository userActionLogRepository;
    @Autowired private ReportingNotificationLogRepository notificationLogRepository;

    private Long parentLogId;

    @BeforeEach
    void seedParent() {
        NotificationLog parent = notificationLogRepository.save(NotificationLog.create()
                .projectId(123L)
                .campaignId(4521L)
                .channel(Channel.PUSH)
                .status(NotificationStatus.DELIVERED)
                .messageKey("kafka-msg-parent")
                .sentAt(LocalDateTime.now())
                .build());
        parentLogId = parent.getId();
    }

    @AfterEach
    void cleanup() {
        userActionLogRepository.deleteAll();
        notificationLogRepository.deleteAll();
    }

    private UserActionIngestRequest request(ActionType actionType, String target) {
        return new UserActionIngestRequest(
                parentLogId, 78901L, 23456L, actionType, target,
                "Mozilla/5.0 (iPhone)", ACTION_AT
        );
    }

    @Test
    @DisplayName("정상 요청 → user_action_logs 1 row INSERT + project_id/campaign_id 비정규화 복사")
    void insertsAndCopiesParentDenormalizedFields() {
        UserActionLog saved = service.record(request(ActionType.CLICK, "https://shop.example.com/promo"), null);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLogId()).isEqualTo(parentLogId);
        assertThat(saved.getProjectId())
                .as("부모 notification_logs.project_id 복사")
                .isEqualTo(123L);
        assertThat(saved.getCampaignId())
                .as("부모 notification_logs.campaign_id 복사")
                .isEqualTo(4521L);
        assertThat(saved.getActionType()).isEqualTo(ActionType.CLICK);
        assertThat(saved.getActionTarget()).isEqualTo("https://shop.example.com/promo");
        assertThat(userActionLogRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("logId 미존재 → BusinessException(LOG_NOT_FOUND)")
    void missingParentThrows400() {
        UserActionIngestRequest request = new UserActionIngestRequest(
                99999999L, 78901L, 23456L, ActionType.OPEN, null, null, ACTION_AT
        );

        assertThatThrownBy(() -> service.record(request, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(AnalyticsErrorCode.LOG_NOT_FOUND));
    }

    @Test
    @DisplayName("동일 페이로드 재전송 → 2 rows INSERT (이벤트 로그 중복 허용 — Rule 2)")
    void duplicatesAreAllowed() {
        UserActionIngestRequest request = request(ActionType.OPEN, null);

        service.record(request, null);
        service.record(request, null);

        assertThat(userActionLogRepository.count())
                .as("이벤트 로그 특성상 중복 허용 — 멱등 키 없음")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("OPEN/CLICK/CONVERT/DISMISS 4종 actionType 모두 적재 가능")
    void allActionTypesArePersisted() {
        service.record(request(ActionType.OPEN, null), null);
        service.record(request(ActionType.CLICK, "https://example.com"), null);
        service.record(request(ActionType.CONVERT, null), null);
        service.record(request(ActionType.DISMISS, null), null);

        assertThat(userActionLogRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("CLICK인데 actionTarget이 null → INVALID_ACTION_TARGET")
    void clickWithoutTargetIsRejected() {
        assertThatThrownBy(() -> service.record(request(ActionType.CLICK, null), null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(AnalyticsErrorCode.INVALID_ACTION_TARGET));
    }

    @Test
    @DisplayName("CLICK인데 actionTarget이 비-URL → INVALID_ACTION_TARGET")
    void clickWithInvalidUrlIsRejected() {
        assertThatThrownBy(() -> service.record(request(ActionType.CLICK, "not-a-url"), null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(AnalyticsErrorCode.INVALID_ACTION_TARGET));
    }

    @Test
    @DisplayName("OPEN은 actionTarget이 null이어도 정상 적재 (CLICK만 검증)")
    void openWithoutTargetIsAccepted() {
        UserActionLog saved = service.record(request(ActionType.OPEN, null), null);

        assertThat(saved.getActionTarget()).isNull();
        assertThat(saved.getActionType()).isEqualTo(ActionType.OPEN);
    }

    @Test
    @DisplayName("resolvedProjectId == parent.projectId → 정상 적재 (도메인 검증 통과)")
    void domainValidationPassesWhenProjectMatches() {
        UserActionLog saved = service.record(request(ActionType.OPEN, null), 123L);

        assertThat(saved.getProjectId()).isEqualTo(123L);
    }

    @Test
    @DisplayName("resolvedProjectId != parent.projectId → LOG_NOT_FOUND (정보 노출 방지, ADR-0017)")
    void domainValidationRejectsCrossProjectLogId() {
        // 인증된 키는 다른 프로젝트(999) 소속이지만 요청 logId 는 project=123 의 row
        assertThatThrownBy(() -> service.record(request(ActionType.OPEN, null), 999L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .as("401/403 이 아닌 LOG_NOT_FOUND 로 통일 — logId 존재 사실 노출 방지")
                                .isEqualTo(AnalyticsErrorCode.LOG_NOT_FOUND));
    }
}
