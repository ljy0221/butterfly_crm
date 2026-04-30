package com.b101.butterfly.delivery.entity;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_nlog_project_campaign", columnList = "projectId, campaignId"),
        @Index(name = "idx_nlog_project_user", columnList = "projectId, userId"),
        @Index(name = "idx_nlog_project_channel", columnList = "projectId, channel")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long campaignId;

    private Long userId;

    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryCommand.Channel channel;

    @Column(length = 500)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(length = 50)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime clickedAt;

    @Builder
    public NotificationLog(Long projectId, Long campaignId, Long userId,
                           Long deviceId, DeliveryCommand.Channel channel,
                           String recipient, NotificationStatus status,
                           String errorCode, String errorMessage,
                           LocalDateTime sentAt) {
        this.projectId = projectId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.channel = channel;
        this.recipient = recipient;
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
