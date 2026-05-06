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
@Table(name = "event_schema_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSchemaField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "field_id")
    private Long id;

    @Column(name = "schema_id", nullable = false)
    private Long schemaId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 10)
    private FieldType fieldType;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Column(name = "is_promoted", nullable = false)
    private boolean isPromoted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_names", columnDefinition = "json")
    private String previousNames;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public EventSchemaField(Long schemaId, String fieldName, FieldType fieldType,
                            boolean isRequired, boolean isPromoted, String previousNames) {
        this.schemaId = schemaId;
        this.fieldName = fieldName;
        this.fieldType = fieldType != null ? fieldType : FieldType.STRING;
        this.isRequired = isRequired;
        this.isPromoted = isPromoted;
        this.previousNames = previousNames;
    }
}
