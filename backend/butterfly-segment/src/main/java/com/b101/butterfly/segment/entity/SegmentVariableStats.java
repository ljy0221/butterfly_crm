package com.b101.butterfly.segment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "segment_variable_stats")
@IdClass(SegmentVariableStatsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SegmentVariableStats {

    @Id
    @Column(name = "segment_id")
    private Long segmentId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Id
    @Column(name = "variable_key", length = 128)
    private String variableKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SegmentVariableSourceType sourceType;

    @Column(name = "available_count", nullable = false)
    private long availableCount;

    @Column(name = "missing_count", nullable = false)
    private long missingCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SegmentVariableStatus status;

    @Column(name = "sample_value")
    private String sampleValue;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime updatedAt;

    @Builder
    private SegmentVariableStats(
            Long segmentId,
            Long projectId,
            String variableKey,
            SegmentVariableSourceType sourceType,
            long availableCount,
            long missingCount,
            SegmentVariableStatus status,
            String sampleValue,
            LocalDateTime updatedAt
    ) {
        this.segmentId = segmentId;
        this.projectId = projectId;
        this.variableKey = variableKey;
        this.sourceType = sourceType;
        this.availableCount = availableCount;
        this.missingCount = missingCount;
        this.status = status;
        this.sampleValue = sampleValue;
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public void updateCounts(long availableCount, long missingCount, SegmentVariableStatus status) {
        this.availableCount = availableCount;
        this.missingCount = missingCount;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}
