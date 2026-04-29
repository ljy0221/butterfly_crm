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
        LocalDateTime estimatedAt,
        LocalDateTime lastBuiltAt,
        BuildStatus buildStatus,
        CreatorSummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LinkedCampaignSummary> linkedCampaigns,
        boolean favorited) {
}
