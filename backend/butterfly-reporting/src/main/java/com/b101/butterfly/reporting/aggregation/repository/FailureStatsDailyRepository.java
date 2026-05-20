package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.entity.FailureStatsDaily;
import com.b101.butterfly.reporting.report.dto.FailureCountRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface FailureStatsDailyRepository extends JpaRepository<FailureStatsDaily, Long> {

    List<FailureStatsDaily> findByBucketDate(LocalDate bucketDate);

    /**
     * S14P31B101-33 — 특정 프로젝트·일자의 실패 원인을 (channel, error_code) 단위로
     * 합산하여 failed_count DESC 순으로 반환. 대시보드 "실패 원인 TOP N" 카드용.
     *
     * <p>호출자는 {@link Pageable}로 PageRequest.of(0, limit)을 넘긴다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.report.dto.FailureCountRow(
                f.channel,
                f.errorCode,
                CAST(SUM(f.failedCount) AS long),
                CAST(SUM(f.affectedCampaignCnt) AS integer),
                CAST(SUM(f.affectedUserCnt) AS integer)
            )
            FROM FailureStatsDaily f
            WHERE f.projectId = :projectId
              AND f.bucketDate = :bucketDate
            GROUP BY f.channel, f.errorCode
            ORDER BY SUM(f.failedCount) DESC
            """)
    List<FailureCountRow> findTopByProjectAndDate(
            @Param("projectId") Long projectId,
            @Param("bucketDate") LocalDate bucketDate,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM failure_stats_daily WHERE bucket_date = :bucketDate",
            nativeQuery = true)
    int deleteByBucketDate(@Param("bucketDate") LocalDate bucketDate);

    @Modifying
    @Query(value = """
            INSERT INTO failure_stats_daily
                (project_id, channel, error_code, bucket_date,
                 failed_count, affected_campaign_cnt, affected_user_cnt,
                 first_occurred_at, last_occurred_at,
                 updated_at)
            VALUES
                (:projectId, :channel, :errorCode, :bucketDate,
                 :failedCount, :affectedCampaignCnt, :affectedUserCnt,
                 :firstOccurredAt, :lastOccurredAt,
                 CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                failed_count          = VALUES(failed_count),
                affected_campaign_cnt = VALUES(affected_campaign_cnt),
                affected_user_cnt     = VALUES(affected_user_cnt),
                first_occurred_at     = VALUES(first_occurred_at),
                last_occurred_at      = VALUES(last_occurred_at),
                updated_at            = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(
            @Param("projectId") Long projectId,
            @Param("channel") String channel,
            @Param("errorCode") String errorCode,
            @Param("bucketDate") LocalDate bucketDate,
            @Param("failedCount") int failedCount,
            @Param("affectedCampaignCnt") int affectedCampaignCnt,
            @Param("affectedUserCnt") int affectedUserCnt,
            @Param("firstOccurredAt") LocalDateTime firstOccurredAt,
            @Param("lastOccurredAt") LocalDateTime lastOccurredAt
    );
}
