package com.b101.butterfly.notification.entity;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity(name = "SenderNotificationLog")
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_nlog_project_campaign", columnList = "project_id, campaign_id"),
        @Index(name = "idx_nlog_project_user", columnList = "project_id, user_id"),
        @Index(name = "idx_nlog_project_channel", columnList = "project_id, channel")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "device_id")
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryCommand.Channel channel;

    @Column(length = 500)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "click_url", length = 500)
    private String clickUrl;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Builder
    public NotificationLog(Long projectId, Long campaignId, Long userId,
                           Long deviceId, DeliveryCommand.Channel channel,
                           String recipient, String title, String body, String clickUrl,
                           NotificationStatus status,
                           String errorCode, String errorMessage,
                           LocalDateTime sentAt) {
        this.projectId = projectId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.channel = channel;
        this.recipient = recipient;
        this.title = title;
        this.body = body;
        this.clickUrl = clickUrl;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt;
    }

    public void markDelivered(LocalDateTime deliveredAt) {
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }

    public void markClicked(LocalDateTime clickedAt) {
        this.status = NotificationStatus.CLICKED;
        this.clickedAt = clickedAt;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
