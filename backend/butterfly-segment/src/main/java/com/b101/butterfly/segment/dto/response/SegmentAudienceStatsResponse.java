package com.b101.butterfly.segment.dto.response;

/**
 * 세그먼트 대상자 채널별 통계.
 *
 * @param totalCount 현재 활성 segment_members 사용자 수
 * @param pushOptInCount 푸시 수신 동의 사용자 수
 * @param pushSendableCount 푸시 수신 동의 + 유효 토큰 보유 사용자 수
 */
public record SegmentAudienceStatsResponse(
        Long totalCount,
        Long pushOptInCount,
        Long pushSendableCount
) {

    public static SegmentAudienceStatsResponse zero() {
        return new SegmentAudienceStatsResponse(0L, 0L, 0L);
    }
}
