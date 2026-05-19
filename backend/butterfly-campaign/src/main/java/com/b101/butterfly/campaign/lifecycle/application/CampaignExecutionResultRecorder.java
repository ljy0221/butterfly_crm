package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionRepository;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignType;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 결과 통보 진입점 — worker 의 {@code CampaignExecutionResultConsumer} 가 호출.
 *
 * <p>잘못된 상태 전이(예: READY → SUCCESS)는 {@link CampaignExecution} 도메인이
 * {@link CampaignErrorCode#INVALID_EXECUTION_TRANSITION}으로 거부하며, 본 서비스는
 * 해당 예외를 그대로 위임한다.
 *
 * <p>자동 ENDED 정책 (S14P31B101-284):
 * <ul>
 *   <li>ONE_TIME 캠페인: 1회 발송이 끝나면 캠페인 자체도 ENDED — 자동 {@code campaign.complete()} 호출</li>
 *   <li>RECURRING/AUTOMATION: 회차/이벤트 정책 정의 후 별 트랙에서 처리 — 현재는 ACTIVE 유지</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignExecutionResultRecorder {

    private final CampaignExecutionRepository campaignExecutionRepository;
    private final CampaignRepository campaignRepository;

    /**
     * 발송 성공 결과 기록 (SEND → SUCCESS) + ONE_TIME 캠페인 자동 ENDED.
     *
     * <p>발송 성공/실패 카운트는 본 도메인이 보관하지 않는다 — notification 모듈의
     * notification_logs / campaign_stats 가 per-target / per-campaign 단일 진실 원천이며,
     * 본 진입점은 회차 단위 status 전이 marker 역할만 한다.
     *
     * @param executionId  CampaignExecution PK
     * @throws BusinessException INVALID_EXECUTION_TRANSITION — 잘못된 상태 전이
     */
    @Transactional
    public void recordCompletion(Long executionId) {
        log.info("캠페인 실행 성공 기록: executionId={}", executionId);
        CampaignExecution execution = findExecution(executionId);
        execution.markAsSuccess();
        maybeEndCampaign(execution.getCampaignId());
        log.info("캠페인 실행 성공 기록 완료: executionId={}, campaignId={}", executionId, execution.getCampaignId());
    }

    /**
     * 발송 실패 결과 기록 (SEND → FAIL) + ONE_TIME 캠페인 자동 ENDED.
     *
     * <p>실패 사유는 본 도메인이 보관하지 않는다 — per-target 실패 메시지는
     * notification_logs.error_code/error_message 에 적재된다.
     *
     * @param executionId  CampaignExecution PK
     * @throws BusinessException INVALID_EXECUTION_TRANSITION — 잘못된 상태 전이
     */
    @Transactional
    public void recordFailure(Long executionId) {
        log.warn("캠페인 실행 실패 기록: executionId={}", executionId);
        CampaignExecution execution = findExecution(executionId);
        execution.markAsFailed();
        maybeEndCampaign(execution.getCampaignId());
        log.warn("캠페인 실행 실패 기록 완료: executionId={}, campaignId={}", executionId, execution.getCampaignId());
    }

    /**
     * ONE_TIME 캠페인이면 자동으로 ENDED 로 종료. 현재 상태가 ENDED 로 전이 불가능한 경우
     * (이미 ENDED / DRAFT) 는 skip — race condition 또는 사용자가 먼저 명시적 종료한 경우 안전.
     */
    private void maybeEndCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));

        if (campaign.getCampaignType() == CampaignType.ONE_TIME
                && campaign.getCampaignStatus().canTransitionTo(CampaignStatus.ENDED)) {
            campaign.complete();
        }
        // RECURRING / AUTOMATION: 회차/이벤트 정책 정의 후 별 트랙에서 처리.
    }

    private CampaignExecution findExecution(Long executionId) {
        return campaignExecutionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }
}
