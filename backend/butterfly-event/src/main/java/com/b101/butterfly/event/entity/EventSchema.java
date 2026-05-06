package com.b101.butterfly.event.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_schemas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schema_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_def", columnDefinition = "json", nullable = false)
    private String schemaDef;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public EventSchema(Long projectId, Long categoryId, Integer version, String schemaDef,
                       LocalDateTime effectiveFrom, boolean isCurrent, Long createdBy) {
        this.projectId = projectId;
        this.categoryId = categoryId;
        this.version = version;
        this.schemaDef = schemaDef;
        this.effectiveFrom = effectiveFrom;
        this.isCurrent = isCurrent;
        this.createdBy = createdBy;
    }

    public void deactivate() {
        this.isCurrent = false;
    }
}
