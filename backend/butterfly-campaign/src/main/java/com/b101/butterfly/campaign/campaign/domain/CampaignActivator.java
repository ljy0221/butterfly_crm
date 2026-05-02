package com.b101.butterfly.campaign.campaign.domain;

import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.campaign.domain.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.campaign.domain.port.CampaignSendInstructionPublisher;
import com.b101.butterfly.campaign.campaign.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 활성화(DRAFT → ACTIVE) + 발송 트리거 책임.
 *
 * <p>흐름:
 * <ol>
 *   <li>영속 엔티티 로드 + 소유권 검증</li>
 *   <li>도메인 메서드 {@link Campaign#activate} 호출 (state machine 검증)</li>
 *   <li>{@link CampaignExecution#ready} 회차 1, 발송 대상 0 (worker가 fan-out 시 갱신)</li>
 *   <li>{@link CampaignSendInstructionPublisher#publish} 호출 (Kafka 발행)</li>
 *   <li>publish 성공 시 Execution을 SEND로 전이</li>
 * </ol>
 *
 * <p>publish 실패 시 트랜잭션 롤백 → Campaign DRAFT 복원 + Execution row 미생성.
 */
@Service
@RequiredArgsConstructor
public class CampaignActivator {

    private static final int IMMEDIATE_RUN_ROUND = 1;
    private static final int INITIAL_TARGET_COUNT = 0;

    private final CampaignRepository campaignRepository;
    private final CampaignSendInstructionPublisher publisher;
    private final Clock clock;

    @Transactional
    public Long activate(Long campaignId, Long projectId) {
        Campaign campaign = campaignRepository.getByCampaignId(campaignId);
        assertOwnedBy(campaign, projectId);
        assertActivatable(campaign);

        campaign.activate();
        CampaignExecution execution = CampaignExecution.ready(
                campaign, IMMEDIATE_RUN_ROUND, INITIAL_TARGET_COUNT);
        campaign.addExecution(execution);
        campaignRepository.saveAndFlush(campaign);

        publisher.publish(CampaignSendInstruction.of(
                execution.getCampaignExecutionId(),
                campaign.getCampaignId(),
                campaign.getProjectId(),
                campaign.getMessageTemplate().getMessageTemplateId(),
                campaign.getSegmentId(),
                campaign.getChannel(),
                Instant.now(clock)
        ));
        execution.markAsSent();

        return execution.getCampaignExecutionId();
    }

    private void assertOwnedBy(Campaign campaign, Long projectId) {
        if (!campaign.isOwnedBy(projectId)) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
    }

    private void assertActivatable(Campaign campaign) {
        if (!campaign.getCampaignStatus().canTransitionTo(CampaignStatus.ACTIVE)) {
            throw new BusinessException(CampaignErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}