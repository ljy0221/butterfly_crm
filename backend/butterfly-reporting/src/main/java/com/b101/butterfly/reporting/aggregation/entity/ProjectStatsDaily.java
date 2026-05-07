package com.b101.butterfly.reporting.aggregation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "project_stats_daily",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_stats_daily_nat",
                columnNames = {"project_id", "bucket_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectStatsDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;

    @Column(name = "active_user_count", nullable = false)
    private int activeUserCount;

    @Column(name = "new_user_count", nullable = false)
    private int newUserCount;

    @Column(name = "churn_user_count", nullable = false)
    private int churnUserCount;

    @Column(name = "total_sent", nullable = false)
    private int totalSent;

    @Column(name = "total_delivered", nullable = false)
    private int totalDelivered;

    @Column(name = "total_failed", nullable = false)
    private int totalFailed;

    @Column(name = "total_open", nullable = false)
    private int totalOpen;

    @Column(name = "total_click", nullable = false)
    private int totalClick;

    @Column(name = "total_convert", nullable = false)
    private int totalConvert;

    @Column(name = "push_open_rate", precision = 5, scale = 2)
    private BigDecimal pushOpenRate;

    @Column(name = "overall_click_rate", precision = 5, scale = 2)
    private BigDecimal overallClickRate;

    @Column(name = "overall_convert_rate", precision = 5, scale = 2)
    private BigDecimal overallConvertRate;

    @Column(name = "active_campaign_count", nullable = false)
    private int activeCampaignCount;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
