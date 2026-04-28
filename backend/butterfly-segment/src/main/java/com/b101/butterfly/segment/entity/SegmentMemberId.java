package com.b101.butterfly.segment.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * SegmentMember 복합키 — (segmentId, userId).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SegmentMemberId implements Serializable {

    private Long segmentId;
    private Long userId;
}
