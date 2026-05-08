package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.entity.ProjectStatsDaily;
import com.b101.butterfly.reporting.dto.DailyTrendRow;
import com.b101.butterfly.reporting.dto.PeriodSummaryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProjectStatsDailyRepository extends JpaRepository<ProjectStatsDaily, Long> {

    Optional<ProjectStatsDaily> findByProjectIdAndBucketDate(Long projectId, LocalDate bucketDate);

    List<ProjectStatsDaily> findByBucketDate(LocalDate bucketDate);

    @Modifying
    @Query(value = "DELETE FROM project_stats_daily WHERE bucket_date = :bucketDate",
            nativeQuery = true)
    int deleteByBucketDate(@Param("bucketDate") LocalDate bucketDate);

    @Modifying
    @Query(value = """
            INSERT INTO project_stats_daily
                (project_id, bucket_date,
                 active_user_count, new_user_count, churn_user_count,
                 total_sent, total_delivered, total_failed,
                 total_open, total_click, total_convert,
                 push_open_rate, overall_click_rate, overall_convert_rate,
                 active_campaign_count,
                 updated_at)
            VALUES
                (:projectId, :bucketDate,
                 0, 0, 0,
                 :totalSent, :totalDelivered, :totalFailed,
                 :totalOpen, :totalClick, :totalConvert,
                 CASE WHEN :totalDelivered = 0 THEN NULL ELSE ROUND(:totalOpen * 100.0 / :totalDelivered, 2) END,
                 CASE WHEN :totalOpen = 0      THEN NULL ELSE ROUND(:totalClick * 100.0 / :totalOpen, 2) END,
                 CASE WHEN :totalSent = 0      THEN NULL ELSE ROUND(:totalConvert * 100.0 / :totalSent, 2) END,
                 :activeCampaignCount,
                 CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                total_sent             = VALUES(total_sent),
                total_delivered        = VALUES(total_delivered),
                total_failed           = VALUES(total_failed),
                total_open             = VALUES(total_open),
                total_click            = VALUES(total_click),
                total_convert          = VALUES(total_convert),
                push_open_rate         = VALUES(push_open_rate),
                overall_click_rate     = VALUES(overall_click_rate),
                overall_convert_rate   = VALUES(overall_convert_rate),
                active_campaign_count  = VALUES(active_campaign_count),
                updated_at             = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(
            @Param("projectId") Long projectId,
            @Param("bucketDate") LocalDate bucketDate,
            @Param("totalSent") int totalSent,
            @Param("totalDelivered") int totalDelivered,
            @Param("totalFailed") int totalFailed,
            @Param("totalOpen") int totalOpen,
            @Param("totalClick") int totalClick,
            @Param("totalConvert") int totalConvert,
            @Param("activeCampaignCount") int activeCampaignCount
    );

    /**
     * S14P31B101-202 — Dashboard API 57의 기간 합산 (Summary 카드).
     *
     * <p>{@code campaignsSentInPeriod}는 본 PR 단순화 — 기간 내 active_campaign_count의
     * MAX 값을 캠페인 수 추정치로 사용. 정확한 distinct campaign 수는 별도 쿼리로 산출.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.dto.PeriodSummaryRow(
                COALESCE(SUM(CAST(p.totalSent AS long)), 0L),
                COALESCE(SUM(CAST(p.totalDelivered AS long)), 0L),
                COALESCE(SUM(CAST(p.totalOpen AS long)), 0L),
                COALESCE(SUM(CAST(p.totalClick AS long)), 0L),
                COALESCE(SUM(CAST(p.totalConvert AS long)), 0L),
                COALESCE(SUM(CAST(p.totalFailed AS long)), 0L),
                CAST(COALESCE(MAX(p.activeCampaignCount), 0) AS long)
            )
            FROM ProjectStatsDaily p
            WHERE p.projectId = :projectId
              AND p.bucketDate >= :from
              AND p.bucketDate <= :to
            """)
    PeriodSummaryRow aggregatePeriod(
            @Param("projectId") Long projectId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * S14P31B101-202 — Dashboard API 57의 dailyTrend 시계열.
     *
     * <p>날짜별 한 row씩 반환 — 기간 내 데이터 없는 날짜는 결과에 포함되지 않음
     * (클라이언트가 빈 날짜를 0으로 채우는 책임).
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.dto.DailyTrendRow(
                p.bucketDate,
                CAST(p.totalSent AS long),
                CAST(p.totalDelivered AS long),
                CAST(p.totalOpen AS long),
                CAST(p.totalClick AS long),
                CAST(p.totalConvert AS long)
            )
            FROM ProjectStatsDaily p
            WHERE p.projectId = :projectId
              AND p.bucketDate >= :from
              AND p.bucketDate <= :to
            ORDER BY p.bucketDate ASC
            """)
    List<DailyTrendRow> findDailyTrend(
            @Param("projectId") Long projectId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
