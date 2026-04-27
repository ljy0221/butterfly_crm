package com.b101.butterfly.auth.entity;

import com.b101.butterfly.auth.constant.ProjectPlan;
import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "business_no", nullable = false, length = 50)
    private String businessNo;

    @Column(name = "company_auth_no", nullable = false, unique = true, length = 100)
    private String companyAuthNo;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "public_api_key", length = 100)
    private String publicApiKey;

    @Column(name = "fcm_credentials", columnDefinition = "TEXT")
    private String fcmCredentials;

    @Column(name = "daily_limit")
    private Integer dailyLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectPlan plan = ProjectPlan.FREE;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Project(String name, String businessNo, String companyAuthNo, Long ownerId) {
        this.name = name;
        this.businessNo = businessNo;
        this.companyAuthNo = companyAuthNo;
        this.ownerId = ownerId;
        this.plan = ProjectPlan.FREE;
    }

    public void issueApiKey(String publicApiKey) {
        this.publicApiKey = publicApiKey;
    }

    public void updateFcmCredentials(String encryptedJson) {
        this.fcmCredentials = encryptedJson;
    }

    public void updateDailyLimit(Integer dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public void updatePlan(ProjectPlan plan) {
        this.plan = plan;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
