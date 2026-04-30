package com.b101.butterfly.segment.port;

/**
 * EVENT 조건 평가용 포트. Phase 6에서 event 모듈의 {@code event_daily_agg}를 읽어 매칭 유저를 조회.
 *
 * <p>Phase 5는 시그니처 placeholder만 두고 stub이 UnsupportedOperationException을 던진다.
 * 실제 메서드는 Phase 6에서 확정 (period 3종 × minCount × filters).
 */
public interface EventAggReader {

    /**
     * 조건에 매칭되는 user_id를 조회. Phase 6에서 시그니처 구체화.
     */
    java.util.List<Long> queryMatchingUserIds(Object eventCondition);
}
