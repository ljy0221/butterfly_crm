package com.b101.butterfly.segment.api;

import com.b101.butterfly.segment.entity.BuildStatus;

import java.time.LocalDateTime;

/**
 * 외부 모듈이 segment 정보를 참조할 때 사용하는 read-only snapshot.
 *
 * <p>P4 (모듈러 모놀리스 전환 - port SPI 확대). 외부 모듈이 {@code segment.entity.Segment}를 직접
 * import하지 않고 본 record를 통해서만 segment 정보에 접근한다. ArchUnit
 * {@code ModularBoundaryTest}가 외부 모듈의 segment.entity 직접 의존을 차단한다.
 */
public record SegmentSnapshot(
        Long segmentId,
        Long projectId,
        String name,
        String description,
        BuildStatus buildStatus,
        Integer estimatedCount,
        LocalDateTime estimatedAt,
        LocalDateTime lastBuiltAt,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
