package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 캠페인 발송 지시를 consume하여 실제 발송 커맨드로 변환하는 워커.
 *
 * <p>흐름:
 * <ol>
 *   <li>{@code campaign.send.requested} 토픽에서 {@link CampaignSendInstruction} consume</li>
 *   <li>templateId → 메시지 내용 조회</li>
 *   <li>segmentId → 수신자 목록 조회</li>
 *   <li>DeliveryCommand 조립</li>
 *   <li>{@code delivery-requests} 토픽으로 발행</li>
 * </ol>
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class CampaignSendInstructionConsumer {

    private final CampaignSendInstructionProcessor processor;

    @KafkaListener(
            topics = "campaign.send.requested",
            groupId = "worker-campaign-group",
            containerFactory = "campaignSendInstructionListenerContainerFactory"
    )
    public void consume(CampaignSendInstruction instruction) {
        log.info("캠페인 발송 지시 수신: executionId={}, campaignId={}, templateId={}, segmentId={}, channel={}",
                instruction.executionId(),
                instruction.campaignId(),
                instruction.templateId(),
                instruction.segmentId(),
                instruction.channel());

        try {
            processor.process(instruction);
            log.info("캠페인 발송 지시 처리 완료: executionId={}", instruction.executionId());
        } catch (Exception e) {
            log.error("캠페인 발송 지시 처리 실패: executionId={}, error={}",
                    instruction.executionId(), e.getMessage(), e);
            // 결과 통보 흐름이 별 트랙 (notification-send-result producer + worker consumer)
            // 으로 신설되면, 여기서 CampaignExecutionResultRecorder.recordFailure(executionId)
            // 호출로 SEND → FAIL 전이 가능. 현재는 예외를 그대로 propagate.
            throw e;
        }
    }
}
