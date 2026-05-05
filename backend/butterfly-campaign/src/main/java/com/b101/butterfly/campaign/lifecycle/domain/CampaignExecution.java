package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignExecutionStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 캠페인 실행 회차 단위 엔티티 (Unidirectional — Campaign로 향하는 FK는 {@link #campaignId} Long).
 *
 * <p>Campaign와의 컬렉션 양방향 매핑은 두지 않는다. Cascade/orphanRemoval로 인한
 * 의도치 않은 일괄 삭제 + 1차 캐시 일관성 부담을 피하기 위함이다.
 */
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

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

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

    private CampaignExecution(Long campaignId, Integer runRound, Integer targetCount) {
        this.campaignId = campaignId;
        this.runRound = runRound;
        this.campaignExecutionStatus = CampaignExecutionStatus.READY;
        this.targetCount = targetCount;
        this.sentCount = 0;
        this.failedCount = 0;
    }

    public static CampaignExecution ready(
            Long campaignId,
            Integer runRound,
            Integer targetCount
    ) {
        if (campaignId == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_REQUIRED);
        }
        if (runRound == null || runRound < 1) {
            throw new BusinessException(CampaignErrorCode.INVALID_RUN_ROUND);
        }
        if (targetCount == null || targetCount < 0) {
            throw new BusinessException(CampaignErrorCode.INVALID_TARGET_COUNT);
        }

        return new CampaignExecution(campaignId, runRound, targetCount);
    }

    public void markAsSent() {
        if (this.campaignExecutionStatus != CampaignExecutionStatus.READY) {
            throw new BusinessException(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }
        this.campaignExecutionStatus = CampaignExecutionStatus.SEND;
        this.sendStartedAt = LocalDateTime.now();
    }
}
