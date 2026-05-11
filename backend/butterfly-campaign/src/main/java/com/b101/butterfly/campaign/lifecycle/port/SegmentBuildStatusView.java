package com.b101.butterfly.campaign.lifecycle.port;

/**
 * 캠페인 GET 응답에 세그먼트 빌드 상태를 표시하기 위한 경량 뷰 객체.
 *
 * <p>campaign 도메인은 segment.entity.BuildStatus를 직접 알지 않고 String으로 수신한다.
 */
public record SegmentBuildStatusView(Long segmentId, String buildStatus) {
}
