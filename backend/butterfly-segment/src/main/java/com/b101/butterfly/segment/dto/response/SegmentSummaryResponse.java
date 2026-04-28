package com.b101.butterfly.segment.dto.response;

import com.b101.butterfly.segment.entity.BuildStatus;

import java.time.LocalDateTime;

/**
 * 세그먼트 목록 항목 (#38). 03-segment.md §3.
 */
public record SegmentSummaryResponse(
        Long segmentId,
        String name,
        String description,
        Integer estimatedCount,
        BuildStatus buildStatus,
        CreatorSummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer linkedCampaignCount,
        boolean favorited) {
}
