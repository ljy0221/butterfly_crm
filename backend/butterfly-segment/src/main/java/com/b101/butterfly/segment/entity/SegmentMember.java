package com.b101.butterfly.segment.entity;

import com.b101.butterfly.segment.entity.converter.VariablesJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 세그먼트에 편입된 유저 매핑. ERD `segment_members`. user_id FK는 event 모듈 합류 시(Phase 6) 추가.
 */
@Entity
@Table(name = "segment_members")
@IdClass(SegmentMemberId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SegmentMember {

    @Id
    @Column(name = "segment_id")
    private Long segmentId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "matched_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime matchedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Convert(converter = VariablesJsonConverter.class)
    @Column(name = "variables_json", columnDefinition = "JSON")
    private Map<String, String> variables;

    @Column(name = "variables_captured_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime variablesCapturedAt;

    @Builder
    private SegmentMember(
            Long segmentId,
            Long userId,
            LocalDateTime expiresAt,
            Map<String, String> variables,
            LocalDateTime variablesCapturedAt
    ) {
        this.segmentId = segmentId;
        this.userId = userId;
        this.matchedAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.variablesCapturedAt = variablesCapturedAt;
    }

    public void renewExpiration(LocalDateTime newExpiresAt) {
        this.expiresAt = newExpiresAt;
    }

    public void replaceVariables(Map<String, String> variables, LocalDateTime capturedAt) {
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.variablesCapturedAt = capturedAt;
    }
}
