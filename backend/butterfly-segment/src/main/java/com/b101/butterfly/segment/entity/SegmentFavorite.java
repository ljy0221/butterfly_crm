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
 * 멤버의 세그먼트 즐겨찾기. ERD `segment_favorites`.
 */
@Entity
@Table(name = "segment_favorites")
@IdClass(SegmentFavoriteId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SegmentFavorite {

    @Id
    @Column(name = "segment_id")
    private Long segmentId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private SegmentFavorite(Long segmentId, Long memberId) {
        this.segmentId = segmentId;
        this.memberId = memberId;
        this.createdAt = LocalDateTime.now();
    }
}
