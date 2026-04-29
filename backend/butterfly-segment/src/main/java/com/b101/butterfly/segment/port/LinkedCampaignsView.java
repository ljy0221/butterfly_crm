package com.b101.butterfly.segment.port;

import java.util.List;

/**
 * 한 세그먼트에 연결된 캠페인을 상태별로 그룹화한 view. campaign 모듈에서 제공.
 */
public record LinkedCampaignsView(
        List<LinkedCampaignSummary> active,
        List<LinkedCampaignSummary> paused,
        List<LinkedCampaignSummary> draft,
        List<LinkedCampaignSummary> ended) {

    public int totalCount() {
        return active.size() + paused.size() + draft.size() + ended.size();
    }

    public static LinkedCampaignsView empty() {
        return new LinkedCampaignsView(List.of(), List.of(), List.of(), List.of());
    }
}
