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

import java.time.LocalDateTime;

/**
 * 캠페인 × 채널 × 시간 단위 집계 (V2_reporting_baseline 의 campaign_stats_hourly 매핑).
 *
 * <p>S14P31B101-124 (124-A): notification_logs 기반 카운트(pending/sent/delivered/failed)만
 * 정착하고 open/click/convert는 0으로 둔다 — user_action_logs 데이터(123 의존)가 추가되면
 * 같은 hourly 워커에서 함께 채운다.
 *
 * <p>본 엔티티는 운영 코드에서 native UPSERT 쿼리로 INSERT/UPDATE되므로 setter는 노출하지
 * 않는다. JPA persist 경로는 테스트 외에 사용되지 않는다.
 */
@Entity
@Table(
        name = "campaign_stats_hourly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_campaign_stats_hourly_nat",
                columnNames = {"campaign_id", "channel", "bucket_hour"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignStatsHourly {

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

    @Column(name = "bucket_hour", nullable = false)
    private LocalDateTime bucketHour;

    @Column(name = "pending_count", nullable = false)
    private int pendingCount;

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

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
