package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.port.LinkedCampaignChecker;
import com.b101.butterfly.segment.port.LinkedCampaignsView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * campaign 모듈 미합류 시 자동 등록되는 stub. 항상 false / 빈 리스트.
 *
 * <p>campaign 모듈이 합류해 진짜 {@link LinkedCampaignChecker} 구현을 빈으로 등록하면
 * {@code @ConditionalOnMissingBean}으로 본 stub이 자동 비활성화된다 — 세그먼트 모듈 코드 변경 0.
 */
@Component
@ConditionalOnMissingBean(LinkedCampaignChecker.class)
public class StubLinkedCampaignChecker implements LinkedCampaignChecker {

    @Override
    public boolean hasActiveOrSending(Long segmentId) {
        return false;
    }

    @Override
    public LinkedCampaignsView listLinkedCampaigns(Long segmentId) {
        return LinkedCampaignsView.empty();
    }
}
