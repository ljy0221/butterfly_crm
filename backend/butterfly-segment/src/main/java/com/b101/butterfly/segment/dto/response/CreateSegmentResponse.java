package com.b101.butterfly.segment.dto.response;

import com.b101.butterfly.segment.entity.BuildStatus;

import java.time.LocalDateTime;

/**
 * 세그먼트 생성 응답 (#37). 03-segment.md §3.
 */
public record CreateSegmentResponse(
        Long segmentId,
        String name,
        String description,
        BuildStatus buildStatus,
        Integer estimatedCount,
        LocalDateTime estimatedAt,
        LocalDateTime lastBuiltAt,
        Integer schemaVersion,
        Long createdBy,
        LocalDateTime createdAt) {
}
