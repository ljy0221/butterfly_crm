package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
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
    private final CampaignExecutionResultRecorder resultRecorder;

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
            // processor가 알려진 실패(잔액 부족, 대상 없음 등)는 내부적으로 recordFailure()를 호출하고 반환.
            // 여기까지 도달한 예외는 예상치 못한 오류 → 안전망으로 SEND → FAIL 전이.
            try {
                resultRecorder.recordFailure(instruction.executionId());
            } catch (Exception recordEx) {
                log.error("캠페인 실행 실패 기록 중 오류: executionId={}, error={}",
                        instruction.executionId(), recordEx.getMessage(), recordEx);
            }
            throw e;
        }
    }
}
