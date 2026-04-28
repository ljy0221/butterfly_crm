package com.b101.butterfly.segment.entity;

import com.b101.butterfly.global.entity.BaseEntity;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.json.ConditionTreeJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 세그먼트 엔티티. ADR-0006 v2 기반 — condition_tree JSON 단일 소스.
 *
 * <p>schemaVersion 기본값은 ADR-0006 v2 기준 3. ERD의 default 1은 v1 시점 잔재이므로 엔티티에서 v2 기본값을 채워준다.
 */
@Entity
@Table(name = "segments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Segment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "segment_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 100)
    private String description;

    @Convert(converter = ConditionTreeJsonConverter.class)
    @Column(name = "condition_tree", nullable = false, columnDefinition = "JSON")
    private GroupNode conditionTree;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "estimated_count")
    private Integer estimatedCount;

    @Column(name = "estimated_at")
    private LocalDateTime estimatedAt;

    @Column(name = "last_built_at")
    private LocalDateTime lastBuiltAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_status", nullable = false, length = 20)
    private BuildStatus buildStatus;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Segment(
            Long projectId,
            String name,
            String description,
            GroupNode conditionTree,
            Long createdBy) {
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.conditionTree = conditionTree;
        this.createdBy = createdBy;
        this.schemaVersion = 3;
        this.estimatedCount = 0;
        this.buildStatus = BuildStatus.PENDING;
        this.isDeleted = false;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replaceConditionTree(GroupNode newTree) {
        this.conditionTree = newTree;
        this.estimatedCount = null;
        this.estimatedAt = null;
        this.buildStatus = BuildStatus.PENDING;
    }

    public void markBuilding() {
        this.buildStatus = BuildStatus.BUILDING;
    }

    public void markReady(int estimatedCount, LocalDateTime builtAt) {
        this.buildStatus = BuildStatus.READY;
        this.estimatedCount = estimatedCount;
        this.estimatedAt = builtAt;
        this.lastBuiltAt = builtAt;
    }

    public void markFailed() {
        this.buildStatus = BuildStatus.FAILED;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
