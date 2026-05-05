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
 * 세그먼트 엔티티. ADR-0006 v2 + ADR-0015 기반 — condition_tree JSON 단일 소스.
 *
 * <p>schemaVersion 기본값은 ADR-0015 기준 4 (EventNode.categoryId 정규화 반영).
 * ERD의 default 1은 v1 시점 잔재이므로 엔티티에서 v4 기본값을 채워준다.
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

    /**
     * 빌드 잡 worker 인스턴스 식별자 ({@link java.util.UUID#randomUUID} 문자열).
     *
     * <p>Codex 합의 안전장치 — 빌드 시작 시 worker가 생성한 token을 기록하고, chunk 처리·완료 시
     * {@code WHERE id = ? AND build_token = ?} 조건으로 stale worker가 결과를 덮어쓰지 못하게 한다.
     * 빌드가 끝나면 null로 되돌린다.
     */
    @Column(name = "build_token", length = 36)
    private String buildToken;

    /**
     * 빌드 시작 시각. {@code BUILDING} 진입 시 기록되고, 완료 후에도 마지막 시작 시각으로 보존.
     */
    @Column(name = "build_started_at")
    private LocalDateTime buildStartedAt;

    /**
     * 빌드 chunk 처리 중 worker가 주기적으로 갱신. {@code BUILDING}이지만
     * {@code build_heartbeat_at < now - timeout} 인 row는 stale로 간주 — 복구 cron이 FAILED 전환,
     * rebuild API는 새 빌드 허용. 빌드가 끝나면 null로 되돌린다.
     */
    @Column(name = "build_heartbeat_at")
    private LocalDateTime buildHeartbeatAt;

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
        this.schemaVersion = 4;
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

    /**
     * 빌드 시작 — {@code BUILDING}으로 전환하고 worker 인스턴스 token + 시작 시각 + 첫 heartbeat 기록.
     *
     * <p>도메인 단순 호출용. 실제 동시성 안전한 전환은 {@code SegmentRepository#markBuildingIfPossible}의
     * CAS 쿼리에서 수행한다.
     */
    public void startBuild(String buildToken, LocalDateTime startedAt) {
        this.buildStatus = BuildStatus.BUILDING;
        this.buildToken = buildToken;
        this.buildStartedAt = startedAt;
        this.buildHeartbeatAt = startedAt;
    }

    /** chunk 처리 중 worker가 호출. CAS 안전성은 Repository 쿼리에서 token 비교로 보장. */
    public void heartbeat(LocalDateTime now) {
        this.buildHeartbeatAt = now;
    }

    /**
     * 빌드 성공 — {@code READY}로 전환하고 token/heartbeat 정리. {@code buildStartedAt}은 마지막 시작
     * 시각으로 보존(재시작 추적).
     */
    public void markReady(int estimatedCount, LocalDateTime builtAt) {
        this.buildStatus = BuildStatus.READY;
        this.estimatedCount = estimatedCount;
        this.estimatedAt = builtAt;
        this.lastBuiltAt = builtAt;
        this.buildToken = null;
        this.buildHeartbeatAt = null;
    }

    /** 빌드 실패 — {@code FAILED}로 전환하고 token/heartbeat 정리. */
    public void markFailed() {
        this.buildStatus = BuildStatus.FAILED;
        this.buildToken = null;
        this.buildHeartbeatAt = null;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
