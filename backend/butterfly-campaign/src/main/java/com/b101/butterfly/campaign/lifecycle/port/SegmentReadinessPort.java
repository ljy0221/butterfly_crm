package com.b101.butterfly.campaign.lifecycle.port;

import java.util.Optional;

/**
 * 캠페인 ACTIVE 전이 시점에 세그먼트 발송 가능 상태(READY) 검증을 위한 out-port.
 *
 * <p>도메인은 본 인터페이스를 통해서만 segment 모듈에 접근한다.
 * adapter는 butterfly-api 조립 모듈에 위치한다 (BC ACL 패턴).
 */
public interface SegmentReadinessPort {

    /**
     * 세그먼트가 발송 가능한 상태(READY)인지 확인하고, 아니면 BusinessException을 던진다.
     *
     * @throws com.b101.butterfly.global.error.BusinessException
     *         SEGMENT_NOT_FOUND — segmentId 미존재
     *         SEGMENT_NOT_READY — buildStatus != READY
     */
    void requireReady(Long segmentId);

    /**
     * UI 응답 (GET /campaigns/{id}) 에 표시할 buildStatus 조회. 검증 없이 raw 반환.
     * 미존재 시 Optional.empty().
     */
    Optional<SegmentBuildStatusView> findStatus(Long segmentId);
}
