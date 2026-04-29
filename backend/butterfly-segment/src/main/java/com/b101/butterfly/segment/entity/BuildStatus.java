package com.b101.butterfly.segment.entity;

/**
 * Materialized Segment 빌드 상태. ERD `segments.build_status`.
 *
 * <p>전이: {@code PENDING} → {@code BUILDING} → {@code READY} 또는 {@code FAILED}.
 */
public enum BuildStatus {
    PENDING,
    BUILDING,
    READY,
    FAILED
}
