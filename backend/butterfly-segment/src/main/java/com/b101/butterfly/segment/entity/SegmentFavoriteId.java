package com.b101.butterfly.segment.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * SegmentFavorite 복합키 — (segmentId, memberId).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SegmentFavoriteId implements Serializable {

    private Long segmentId;
    private Long memberId;
}
