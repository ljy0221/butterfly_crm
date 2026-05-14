package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 캠페인 회차 발송 결과 통보 consumer — {@code SEND → SUCCESS|FAIL} 전이를 수행한다.
 *
 * <p>판정 logic 의 단일 지점 (도메인 사양 S14P31B101-284):
 * <ul>
 *   <li>{@code sentCount >= 1} → {@link CampaignExecutionResultRecorder#recordCompletion}</li>
 *   <li>{@code sentCount == 0} → {@link CampaignExecutionResultRecorder#recordFailure}</li>
 * </ul>
 *
 * <p>실패 사유는 본 도메인이 보관하지 않으며, per-target 실패 메시지는 notification 의
 * {@code notification_logs.error_code/error_message} 가 단일 진실 원천이다.
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class CampaignExecutionResultConsumer {

    private final CampaignExecutionResultRecorder campaignExecutionResultRecorder;

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
            if (result.sentCount() >= 1) {
                campaignExecutionResultRecorder.recordCompletion(result.executionId());
            } else {
                campaignExecutionResultRecorder.recordFailure(result.executionId());
            }
            log.info("CampaignExecution status 전이 완료: executionId={}, verdict={}",
                    result.executionId(),
                    result.sentCount() >= 1 ? "SUCCESS" : "FAIL");
        } catch (Exception e) {
            log.error("CampaignExecution status 전이 실패: executionId={}, error={}",
                    result.executionId(), e.getMessage(), e);
            throw e;
        }
    }
}
