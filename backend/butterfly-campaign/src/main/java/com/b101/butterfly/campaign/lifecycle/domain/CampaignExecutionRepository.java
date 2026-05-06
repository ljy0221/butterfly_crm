package com.b101.butterfly.campaign.lifecycle.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignExecutionRepository extends JpaRepository<CampaignExecution, Long> {

    /**
     * reporting BC 정합성 검증용 — Campaign와 JOIN하여 projectId/channel를 함께 적재한다.
     * sendStartedAt이 null일 때는 회차의 트리거 시점을 알 수 없으므로 createdAt을 fallback으로 사용한다.
     */
    @Query("""
            SELECT new com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionInfoProjection(
                e.campaignExecutionId, e.campaignId, c.projectId, c.channel,
                e.targetCount, COALESCE(e.sendStartedAt, e.createdAt))
            FROM CampaignExecution e
            JOIN Campaign c ON c.campaignId = e.campaignId
            WHERE e.campaignExecutionId = :executionId
            """)
    Optional<CampaignExecutionInfoProjection> findExecutionInfoById(@Param("executionId") Long executionId);

    @Query("""
            SELECT new com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionInfoProjection(
                e.campaignExecutionId, e.campaignId, c.projectId, c.channel,
                e.targetCount, COALESCE(e.sendStartedAt, e.createdAt))
            FROM CampaignExecution e
            JOIN Campaign c ON c.campaignId = e.campaignId
            WHERE e.campaignId = :campaignId
            """)
    List<CampaignExecutionInfoProjection> findExecutionInfosByCampaignId(@Param("campaignId") Long campaignId);

    @Query("""
            SELECT new com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionInfoProjection(
                e.campaignExecutionId, e.campaignId, c.projectId, c.channel,
                e.targetCount, COALESCE(e.sendStartedAt, e.createdAt))
            FROM CampaignExecution e
            JOIN Campaign c ON c.campaignId = e.campaignId
            WHERE COALESCE(e.sendStartedAt, e.createdAt) >= :since
            """)
    List<CampaignExecutionInfoProjection> findExecutionInfosSince(@Param("since") LocalDateTime since);
}
