package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.entity.ProjectStatsDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
                 :pushOpenRate, :overallClickRate, :overallConvertRate,
                 0,
                 CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                total_sent           = VALUES(total_sent),
                total_delivered      = VALUES(total_delivered),
                total_failed         = VALUES(total_failed),
                total_open           = VALUES(total_open),
                total_click          = VALUES(total_click),
                total_convert        = VALUES(total_convert),
                push_open_rate       = VALUES(push_open_rate),
                overall_click_rate   = VALUES(overall_click_rate),
                overall_convert_rate = VALUES(overall_convert_rate),
                updated_at           = CURRENT_TIMESTAMP
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
            @Param("pushOpenRate") BigDecimal pushOpenRate,
            @Param("overallClickRate") BigDecimal overallClickRate,
            @Param("overallConvertRate") BigDecimal overallConvertRate
    );
}
