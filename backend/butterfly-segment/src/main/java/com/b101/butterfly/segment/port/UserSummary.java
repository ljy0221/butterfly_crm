package com.b101.butterfly.segment.port;

import java.time.LocalDateTime;

/**
 * 세그먼트 대상 유저 목록 응답에 필요한 user 요약 정보. event 모듈 합류 시 진짜 데이터로 채움.
 *
 * <p>{@link #lastActiveAt}는 v1에서 항상 {@code null} — last_active_at 컬럼이 ERD에 정의되지
 * 않았고, {@code events.MAX(occurred_at)} 또는 {@code event_daily_agg.MAX(log_date)}로의 추출은
 * v1.5/v2 후속 task에서 정밀화. API 문서/프론트 타입은 nullable로 표기.
 */
public record UserSummary(
        Long userId,
        String externalId,
        String email,
        LocalDateTime lastActiveAt) {
}
