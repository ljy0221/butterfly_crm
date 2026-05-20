package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.port.LinkedCampaignChecker;
import com.b101.butterfly.segment.port.LinkedCampaignsView;

/**
 * campaign 모듈 미합류 시 stub. 항상 false / 빈 리스트.
 *
 * <p>{@link com.b101.butterfly.segment.config.StubPortConfig}가 {@code @ConditionalOnMissingBean}으로
 * 등록한다 — campaign 모듈이 진짜 빈을 등록하면 본 stub은 자동 비활성화된다.
 */
public class StubLinkedCampaignChecker implements LinkedCampaignChecker {

    @Override
    public boolean hasActiveOrSending(Long segmentId) {
        return false;
    }

    @Override
    public LinkedCampaignsView listLinkedCampaigns(Long segmentId) {
        return LinkedCampaignsView.empty();
    }

    @Override
    public int countLinkedCampaigns(Long segmentId) {
        return 0;
    }
}
