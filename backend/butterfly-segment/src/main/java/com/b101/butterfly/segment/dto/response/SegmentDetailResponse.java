package com.b101.butterfly.segment.dto.response;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.entity.BuildStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세그먼트 상세 응답 (#39). 03-segment.md §3.
 */
public record SegmentDetailResponse(
        Long segmentId,
        String name,
        String description,
        GroupNode conditionTree,
        Integer schemaVersion,
        Integer estimatedCount,
        SegmentAudienceStatsResponse audienceStats,
        LocalDateTime estimatedAt,
        LocalDateTime lastBuiltAt,
        BuildStatus buildStatus,
        /** 빌드 중일 때는 실시간 segment_members COUNT(*), 그 외 상태에서는 estimatedCount 와 동일.
         *  프론트가 BUILDING 동안 progress UI 로 사용 (S14P31B101-275). */
        Long currentMemberCount,
        LocalDateTime buildStartedAt,
        CreatorSummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LinkedCampaignSummary> linkedCampaigns,
        boolean favorited) {
}
