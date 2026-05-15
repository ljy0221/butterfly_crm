package com.b101.butterfly.segment.dto.response;

import com.b101.butterfly.segment.entity.BuildStatus;

import java.time.LocalDateTime;

/**
 * 빌드 취소 API 응답 (S14P31B101-269).
 *
 * @param segmentId 취소된 segment ID
 * @param buildStatus 취소 후 상태 (항상 {@code FAILED})
 * @param canceledAt 취소 처리 시각
 */
public record CancelBuildResponse(Long segmentId, BuildStatus buildStatus, LocalDateTime canceledAt) {
}
