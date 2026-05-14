package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionRepository;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 결과 통보 진입점 — worker fan-out 완료 후 호출된다.
 *
 * <p>잘못된 상태 전이(예: READY → SUCCESS)는 {@link CampaignExecution} 도메인이
 * {@link CampaignErrorCode#INVALID_EXECUTION_TRANSITION}으로 거부하며, 본 서비스는
 * 해당 예외를 그대로 위임한다.
 */
@Service
@RequiredArgsConstructor
public class CampaignExecutionResultRecorder {

    private final CampaignExecutionRepository campaignExecutionRepository;

    /**
     * 발송 성공 결과 기록 (SEND → SUCCESS).
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
        CampaignExecution execution = findExecution(executionId);
        execution.markAsSuccess();
    }

    /**
     * 발송 실패 결과 기록 (SEND → FAIL).
     *
     * <p>실패 사유는 본 도메인이 보관하지 않는다 — per-target 실패 메시지는
     * notification_logs.error_code/error_message 에 적재된다.
     *
     * @param executionId  CampaignExecution PK
     * @throws BusinessException INVALID_EXECUTION_TRANSITION — 잘못된 상태 전이
     */
    @Transactional
    public void recordFailure(Long executionId) {
        CampaignExecution execution = findExecution(executionId);
        execution.markAsFailed();
    }

    private CampaignExecution findExecution(Long executionId) {
        return campaignExecutionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }
}
