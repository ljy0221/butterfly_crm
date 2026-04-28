package com.b101.butterfly.segment.dto.response;

import java.time.LocalDateTime;

/**
 * 세그먼트 대상 유저 항목 (#43). customer 모듈 합류 후 email/lastActiveAt 채움.
 */
public record SegmentUserResponse(
        Long userId,
        String externalId,
        String email,
        LocalDateTime lastActiveAt,
        LocalDateTime matchedAt) {
}
