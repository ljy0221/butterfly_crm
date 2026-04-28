package com.b101.butterfly.segment.dto.response;

import java.util.List;

/**
 * 세그먼트 사용 캠페인 목록 (#45). 캠페인 상태별 그룹화.
 */
public record LinkedCampaignsResponse(
        List<LinkedCampaignSummary> active,
        List<LinkedCampaignSummary> paused,
        List<LinkedCampaignSummary> draft,
        List<LinkedCampaignSummary> ended,
        int totalCount) {
}
