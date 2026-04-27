package com.b101.butterfly.campaign.service.campaign.domain;

import com.b101.butterfly.campaign.service.campaign.domain.enums.CampaignExecutionStatus;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE campaign_executions SET is_deleted = true, deleted_at = NOW() WHERE campaign_execution_id = ?")
@SQLRestriction("is_deleted = false")
public class CampaignExecution extends SoftDeletedDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campaign_execution_id")
    private Long campaignExecutionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "run_round", nullable = false)
    private Integer runRound;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_execution_status", nullable = false)
    private CampaignExecutionStatus campaignExecutionStatus;

    @Column(name = "send_started_at")
    private LocalDateTime sendStartedAt;

    @Column(name = "send_completed_at")
    private LocalDateTime sendCompletedAt;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    @Column(name = "sent_count", nullable = false)
    private Integer sentCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Column(name = "fail_reason")
    private String failReason;

    public void assignCampaign(Campaign campaign) {
        this.campaign = campaign;
    }
}