package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 캠페인 실행 결과를 받아 크레딧을 정산하고 실행 상태를 SUCCESS/FAIL로 전이한다.
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class CampaignExecutionResultConsumer {

    private final CampaignExecutionBillingService campaignExecutionBillingService;

    @KafkaListener(
            topics = "campaign-execution-result",
            groupId = "worker-campaign-execution-result-group",
            containerFactory = "campaignExecutionResultListenerContainerFactory"
    )
    public void consume(CampaignExecutionResult result) {
        log.info("CampaignExecutionResult 수신: executionId={}, campaignId={}, sent={}, failed={}",
                result.executionId(),
                result.campaignId(),
                result.sentCount(),
                result.failedCount());

        try {
            campaignExecutionBillingService.settleAndRecord(result);
            log.info("CampaignExecution 정산/상태 전이 완료: executionId={}, verdict={}",
                    result.executionId(),
                    result.sentCount() >= 1 ? "SUCCESS" : "FAIL");
        } catch (Exception e) {
            log.error("CampaignExecution 정산/상태 전이 실패: executionId={}, error={}",
                    result.executionId(), e.getMessage(), e);
            throw e;
        }
    }
}
