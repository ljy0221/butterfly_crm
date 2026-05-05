package com.b101.butterfly.reporting.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송 로그 엔티티 (V1__create_notification_logs.sql + V13__add_notification_logs_message_key.sql).
 *
 * <p>외부 발송 워커(이서연/구동엽 팀)가 Kafka 토픽 `notification-send-result`로 발행한
 * 결과를 reporting 모듈이 1건 = 1 row로 적재한다. 컨슈머 구현은 본 티켓 범위 외 —
 * S14P31B101-177(122-A2)에서 다룬다.
 *
 * <p><b>상태 전이</b>: 초기 INSERT 후 status는 {@code PENDING → SENT → DELIVERED} 또는
 * {@code SENT → FAILED} 등으로 UPDATE된다. 전이 가능 여부는
 * {@link NotificationStatus#canTransitionTo(NotificationStatus)}로 검증하고,
 * 통과한 경우에만 {@link #applyTransition(NotificationStatus, java.time.LocalDateTime,
 * java.time.LocalDateTime, String, String)}으로 갱신한다 (setter 미노출).
 *
 * <p><b>멱등성</b>: {@link #messageKey}에 UNIQUE INDEX가 걸려 있으므로 동일 키 재INSERT는
 * DB 레벨에서 차단된다 (V13).
 */
@Entity
@Table(name = "notification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "campaign_execution_id")
    private Long campaignExecutionId;

    @Column(name = "message_template_id")
    private Long messageTemplateId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "device_id")
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "message_key", length = 128, unique = true)
    private String messageKey;

    @Builder(builderMethodName = "create", access = AccessLevel.PUBLIC)
    private NotificationLog(
            Long projectId,
            Long campaignId,
            Long campaignExecutionId,
            Long messageTemplateId,
            Long userId,
            Long deviceId,
            Channel channel,
            NotificationStatus status,
            String errorCode,
            String errorMessage,
            LocalDateTime sentAt,
            LocalDateTime deliveredAt,
            String messageKey
    ) {
        this.projectId = projectId;
        this.campaignId = campaignId;
        this.campaignExecutionId = campaignExecutionId;
        this.messageTemplateId = messageTemplateId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.channel = channel;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt;
        this.deliveredAt = deliveredAt;
        this.messageKey = messageKey;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 상태 전이 적용. 호출자는 사전에
     * {@link NotificationStatus#canTransitionTo(NotificationStatus)}로 가능 여부를 확인해야 한다.
     * 같은 status 재수신은 멱등이므로 호출해도 무방하다 (필드는 새 값으로 덮어쓴다).
     *
     * <p>각 인자는 nullable — 들어온 값으로만 갱신하면 되는 케이스(예: SENT→DELIVERED 시
     * sentAt은 그대로 유지)도 있으므로 null이면 기존 값을 유지한다.
     */
    public void applyTransition(
            NotificationStatus newStatus,
            LocalDateTime newSentAt,
            LocalDateTime newDeliveredAt,
            String newErrorCode,
            String newErrorMessage
    ) {
        this.status = newStatus;
        if (newSentAt != null) {
            this.sentAt = newSentAt;
        }
        if (newDeliveredAt != null) {
            this.deliveredAt = newDeliveredAt;
        }
        if (newErrorCode != null) {
            this.errorCode = newErrorCode;
        }
        if (newErrorMessage != null) {
            this.errorMessage = newErrorMessage;
        }
    }
}
