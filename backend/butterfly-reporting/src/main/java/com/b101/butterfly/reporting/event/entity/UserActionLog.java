package com.b101.butterfly.reporting.event.entity;

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
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 사용자 반응 로그 엔티티 (V2_reporting_baseline 의 user_action_logs 매핑).
 *
 * <p>S14P31B101-123 (123-A): SDK가 발행한 OPEN/CLICK/CONVERT/DISMISS 이벤트가 본 엔티티로
 * 적재된다. notification_logs : user_action_logs = 1 : N (한 발송에 OPEN 1번 + CLICK 2번 등
 * 다회 반응 가능).
 *
 * <p><b>Append-only</b>: Hibernate {@code @Immutable} + setter 미노출. UPDATE/DELETE 가
 * 도메인 코드에서 가능하지 않게 한다 (audit_logs S14P31B101-125 와 같은 패턴).
 *
 * <p><b>비정규화</b>: project_id, campaign_id 는 부모 notification_logs 에서 복사 (Rule 2).
 * 쓰기 시점에 동기화는 {@code UserActionIngestService} 가 책임진다.
 */
@Entity
@Immutable
@Table(name = "user_action_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Long id;

    @Column(name = "log_id", nullable = false)
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
    @Column(name = "action_type", nullable = false, length = 16)
    private ActionType actionType;

    @Column(name = "action_target", length = 500)
    private String actionTarget;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(builderMethodName = "create", access = AccessLevel.PUBLIC)
    private UserActionLog(
            Long logId,
            Long projectId,
            Long campaignId,
            Long userId,
            Long deviceId,
            ActionType actionType,
            String actionTarget,
            String userAgent,
            LocalDateTime actionAt
    ) {
        this.logId = logId;
        this.projectId = projectId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.actionType = actionType;
        this.actionTarget = actionTarget;
        this.userAgent = userAgent;
        this.actionAt = actionAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
