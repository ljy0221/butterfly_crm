package com.b101.butterfly.delivery.entity;

import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignStats extends BaseEntity {

    @Id
    private Long campaignId;

    @Column(nullable = false)
    private int totalTarget;

    @Column(nullable = false)
    private int sentCount;

    @Column(nullable = false)
    private int deliveredCount;

    @Column(nullable = false)
    private int clickedCount;

    @Column(nullable = false)
    private int failedCount;

    public CampaignStats(Long campaignId, int totalTarget) {
        this.campaignId = campaignId;
        this.totalTarget = totalTarget;
        this.sentCount = 0;
        this.deliveredCount = 0;
        this.clickedCount = 0;
        this.failedCount = 0;
    }

    public void incrementSent() {
        this.sentCount++;
    }

    public void incrementDelivered() {
        this.deliveredCount++;
    }

    public void incrementClicked() {
        this.clickedCount++;
    }

    public void incrementFailed() {
        this.failedCount++;
    }
}
