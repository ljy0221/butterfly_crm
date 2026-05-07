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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "failure_stats_daily",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_failure_stats_daily_nat",
                columnNames = {"project_id", "channel", "error_code", "bucket_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailureStatsDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    @Column(name = "error_code", nullable = false, length = 50)
    private String errorCode;

    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "affected_campaign_cnt", nullable = false)
    private int affectedCampaignCnt;

    @Column(name = "affected_user_cnt", nullable = false)
    private int affectedUserCnt;

    @Column(name = "first_occurred_at")
    private LocalDateTime firstOccurredAt;

    @Column(name = "last_occurred_at")
    private LocalDateTime lastOccurredAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
