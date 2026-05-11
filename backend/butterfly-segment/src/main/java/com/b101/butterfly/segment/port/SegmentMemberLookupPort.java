package com.b101.butterfly.segment.port;

import java.util.List;

/**
 * 세그먼트 멤버 조회를 위한 out port.
 *
 * <p>Worker가 캠페인 발송 시 세그먼트에 속한 사용자 목록을 조회하기 위한 인터페이스.
 * segment BC가 외부 BC(worker)에 제공하는 read-only 진입점.
 */
public interface SegmentMemberLookupPort {

    /**
     * 세그먼트에 속한 활성(미만료) 사용자 ID 목록을 조회한다.
     *
     * <p>segmentId가 null이면 빈 리스트 반환 (전체 발송은 상위 레이어에서 처리).
     *
     * @param segmentId 세그먼트 ID (nullable)
     * @return 사용자 ID 목록 (활성 멤버만, 만료된 멤버 제외)
     */
    List<Long> getActiveUserIds(Long segmentId);
}
