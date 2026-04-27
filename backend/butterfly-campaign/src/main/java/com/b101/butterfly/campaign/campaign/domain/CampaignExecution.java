package com.b101.butterfly.campaign.campaign.domain;

import com.b101.butterfly.campaign.campaign.domain.enums.CampaignExecutionStatus;
import com.b101.butterfly.campaign.campaign.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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

    private CampaignExecution(Campaign campaign, Integer runRound, Integer targetCount) {
        this.campaign = campaign;
        this.runRound = runRound;
        this.campaignExecutionStatus = CampaignExecutionStatus.READY;
        this.targetCount = targetCount;
        this.sentCount = 0;
        this.failedCount = 0;
    }

    public static CampaignExecution ready(Campaign campaign, Integer runRound,
            Integer targetCount) {
        if (campaign == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_REQUIRED);
        }
        if (runRound == null || runRound < 1) {
            throw new BusinessException(CampaignErrorCode.INVALID_RUN_ROUND);
        }
        if (targetCount == null || targetCount < 0) {
            throw new BusinessException(CampaignErrorCode.INVALID_TARGET_COUNT);
        }

        return new CampaignExecution(campaign, runRound, targetCount);
    }

    void assignCampaign(Campaign campaign) {
        this.campaign = campaign;
    }
}
