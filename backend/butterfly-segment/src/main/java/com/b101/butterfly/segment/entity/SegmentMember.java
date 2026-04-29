package com.b101.butterfly.segment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 세그먼트에 편입된 유저 매핑. ERD `segment_members`. user_id FK는 customer 모듈 합류 시(Phase 6) 추가.
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

    @Builder
    private SegmentMember(Long segmentId, Long userId, LocalDateTime expiresAt) {
        this.segmentId = segmentId;
        this.userId = userId;
        this.matchedAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public void renewExpiration(LocalDateTime newExpiresAt) {
        this.expiresAt = newExpiresAt;
    }
}
