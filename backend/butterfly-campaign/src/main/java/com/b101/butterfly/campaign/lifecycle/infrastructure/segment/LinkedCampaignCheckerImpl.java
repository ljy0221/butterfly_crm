package com.b101.butterfly.campaign.lifecycle.infrastructure.segment;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.segment.port.LinkedCampaignChecker;
import com.b101.butterfly.segment.port.LinkedCampaignSummary;
import com.b101.butterfly.segment.port.LinkedCampaignsView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * butterfly-segment 모듈이 정의한 {@link LinkedCampaignChecker} 의 campaign-side 구현체.
 *
 * <p>segment 는 본 어댑터를 모르고 port 만 의존한다. segment 측 stub
 * {@code StubLinkedCampaignChecker} 는 {@code @ConditionalOnMissingBean} 으로 자동 back-off.
 *
 * <p>차단 status = {ACTIVE, PAUSED}. PAUSED → ACTIVE 전이가 허용되므로
 * ({@link CampaignStatus} 전이 매트릭스) PAUSED 동안 segment 가 바뀌면 resume 시점에
 * 의도와 다른 발송이 재개되는 위험. DRAFT 는 활성화 시점 별도 검증으로 보호,
 * ENDED 는 terminal 이라 영향 없음.
 *
 * <p>대량 캠페인 환경에서도 OOM 없도록 hasActiveOrSending 은 exists count 쿼리 사용.
 */
@Component
@RequiredArgsConstructor
public class LinkedCampaignCheckerImpl implements LinkedCampaignChecker {

    private static final List<CampaignStatus> BLOCKING_STATUSES =
            List.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED);

    private final CampaignRepository campaignRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrSending(Long segmentId) {
        if (segmentId == null) {
            return false;
        }
        return campaignRepository.existsBySegmentIdAndCampaignStatusIn(segmentId, BLOCKING_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public LinkedCampaignsView listLinkedCampaigns(Long segmentId) {
        if (segmentId == null) {
            return LinkedCampaignsView.empty();
        }
        List<Campaign> campaigns = campaignRepository.findAllBySegmentId(segmentId);

        Map<CampaignStatus, List<LinkedCampaignSummary>> grouped =
                new EnumMap<>(CampaignStatus.class);
        for (CampaignStatus status : CampaignStatus.values()) {
            grouped.put(status, new ArrayList<>());
        }
        for (Campaign c : campaigns) {
            grouped.get(c.getCampaignStatus()).add(toSummary(c));
        }
        return new LinkedCampaignsView(
                grouped.get(CampaignStatus.ACTIVE),
                grouped.get(CampaignStatus.PAUSED),
                grouped.get(CampaignStatus.DRAFT),
                grouped.get(CampaignStatus.ENDED));
    }

    @Override
    @Transactional(readOnly = true)
    public int countLinkedCampaigns(Long segmentId) {
        if (segmentId == null) {
            return 0;
        }
        return Math.toIntExact(campaignRepository.countBySegmentId(segmentId));
    }

    private static LinkedCampaignSummary toSummary(Campaign c) {
        return new LinkedCampaignSummary(
                c.getCampaignId(), c.getCampaignName(), c.getCampaignStatus().name());
    }
}
