package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import com.b101.butterfly.payment.application.CreditAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 실행 결과 상태 전이와 크레딧 정산을 같은 DB 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
@Profile("worker")
@RequiredArgsConstructor
public class CampaignExecutionBillingService {

    private final CreditAccountService creditAccountService;
    private final CampaignExecutionResultRecorder campaignExecutionResultRecorder;

    @Transactional
    public void settleAndRecord(CampaignExecutionResult result) {
        log.info("캠페인 실행 정산 시작: executionId={}, sentCount={}, failedCount={}",
                result.executionId(), result.sentCount(), result.failedCount());
        creditAccountService.settleCampaignExecution(result.executionId(), result.sentCount());
        if (result.sentCount() >= 1) {
            campaignExecutionResultRecorder.recordCompletion(result.executionId());
        } else {
            campaignExecutionResultRecorder.recordFailure(result.executionId());
        }
        log.info("캠페인 실행 정산 완료: executionId={}, verdict={}",
                result.executionId(), result.sentCount() >= 1 ? "SUCCESS" : "FAIL");
    }
}
