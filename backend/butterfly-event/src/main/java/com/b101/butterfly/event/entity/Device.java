package com.b101.butterfly.event.entity;

import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "devices",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_DEVICES_PROJECT_EXTID",
                columnNames = {"project_id", "external_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(name = "push_token", nullable = false, length = 500)
    private String pushToken;

    @Column(name = "push_token_updated_at", nullable = false)
    private LocalDateTime pushTokenUpdatedAt;

    @Column(name = "token_valid", nullable = false)
    private Boolean tokenValid;

    @Column(name = "app_version", nullable = false, length = 50)
    private String appVersion;

    @Column(name = "os_version", nullable = false, length = 50)
    private String osVersion;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Builder
    public Device(Long projectId, Long userId, String externalId, Platform platform,
                  String pushToken, LocalDateTime pushTokenUpdatedAt, Boolean tokenValid,
                  String appVersion, String osVersion, LocalDateTime lastActiveAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.externalId = externalId;
        this.platform = platform;
        this.pushToken = pushToken;
        this.pushTokenUpdatedAt = pushTokenUpdatedAt;
        this.tokenValid = tokenValid != null ? tokenValid : Boolean.TRUE;
        this.appVersion = appVersion;
        this.osVersion = osVersion;
        this.lastActiveAt = lastActiveAt;
    }

    public void rebindUser(Long newUserId) {
        this.userId = newUserId;
    }
}
