package com.b101.butterfly.segment.dto.response;

import com.b101.butterfly.segment.entity.BuildStatus;

import java.time.LocalDateTime;

/**
 * 재빌드 트리거 응답 (#44). 202 Accepted, buildStatus는 BUILDING.
 */
public record RebuildResponse(Long segmentId, BuildStatus buildStatus, LocalDateTime queuedAt) {
}
