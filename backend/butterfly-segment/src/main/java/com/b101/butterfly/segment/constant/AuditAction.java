package com.b101.butterfly.segment.constant;

/**
 * segment 모듈의 감사 액션 코드 ([ADR-0009] 발행자 측 계약).
 *
 * <p>{@code AuditRecordedEvent.action}에 그대로 들어가는 문자열 상수. cross-module audit 저장소
 * (reporting)는 도메인별 enum을 알 필요가 없으므로 String 상수로 둔다 — tenant {@code AuditAction}
 * 패턴을 미러링.
 *
 * <p>{@code SEGMENT_REBUILD_REQUESTED}는 Phase 6에서 {@code SegmentService.rebuild(...)} 도입 시
 * 추가 예정.
 */
public final class AuditAction {

    public static final String SEGMENT_CREATED = "SEGMENT_CREATED";
    public static final String SEGMENT_UPDATED = "SEGMENT_UPDATED";
    public static final String SEGMENT_DELETED = "SEGMENT_DELETED";

    private AuditAction() {}
}
