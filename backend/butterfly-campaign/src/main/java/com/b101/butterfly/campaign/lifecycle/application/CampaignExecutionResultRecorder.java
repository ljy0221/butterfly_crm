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
     * @param executionId  CampaignExecution PK
     * @param sentCount    실제 발송 성공 수
     * @param failedCount  발송 실패 수
     * @throws BusinessException INVALID_EXECUTION_TRANSITION — 잘못된 상태 전이
     */
    @Transactional
    public void recordCompletion(Long executionId, int sentCount, int failedCount) {
        CampaignExecution execution = findExecution(executionId);
        execution.markAsSuccess();
        execution.updateSentCount(sentCount, failedCount);
    }

    /**
     * 발송 실패 결과 기록 (SEND → FAIL).
     *
     * @param executionId  CampaignExecution PK
     * @param reason       실패 사유
     * @throws BusinessException INVALID_EXECUTION_TRANSITION — 잘못된 상태 전이
     */
    @Transactional
    public void recordFailure(Long executionId, String reason) {
        CampaignExecution execution = findExecution(executionId);
        execution.markAsFailed(reason);
    }

    private CampaignExecution findExecution(Long executionId) {
        return campaignExecutionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }
}
