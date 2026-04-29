package com.b101.butterfly.segment.port;

/**
 * 세그먼트가 연결된 캠페인 정보를 조회하는 포트. 실제 구현은 butterfly-campaign 모듈이 등록한다.
 *
 * <p>campaign 모듈 미합류 시 {@code StubLinkedCampaignChecker}가 항상 false/empty를 반환한다.
 */
public interface LinkedCampaignChecker {

    /**
     * 활성 또는 발송 중 상태의 캠페인이 본 세그먼트를 사용하고 있는지.
     * true 반환 시 세그먼트 수정/삭제는 거절된다.
     */
    boolean hasActiveOrSending(Long segmentId);

    /**
     * 세그먼트를 사용 중인 캠페인을 상태별로 그룹화.
     */
    LinkedCampaignsView listLinkedCampaigns(Long segmentId);
}
