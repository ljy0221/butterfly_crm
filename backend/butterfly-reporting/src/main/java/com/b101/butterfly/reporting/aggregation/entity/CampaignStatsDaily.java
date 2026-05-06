package com.b101.butterfly.reporting.aggregation.entity;

import com.b101.butterfly.reporting.notification.entity.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "campaign_stats_daily",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_campaign_stats_daily_nat",
                columnNames = {"campaign_id", "channel", "bucket_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignStatsDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "open_count", nullable = false)
    private int openCount;

    @Column(name = "click_count", nullable = false)
    private int clickCount;

    @Column(name = "convert_count", nullable = false)
    private int convertCount;

    @Column(name = "delivery_rate", precision = 5, scale = 2)
    private BigDecimal deliveryRate;

    @Column(name = "open_rate", precision = 5, scale = 2)
    private BigDecimal openRate;

    @Column(name = "click_rate", precision = 5, scale = 2)
    private BigDecimal clickRate;

    @Column(name = "convert_rate", precision = 5, scale = 2)
    private BigDecimal convertRate;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
