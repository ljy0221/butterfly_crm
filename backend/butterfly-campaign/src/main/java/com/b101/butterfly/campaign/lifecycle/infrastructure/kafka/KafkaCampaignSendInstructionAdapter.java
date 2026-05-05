package com.b101.butterfly.campaign.lifecycle.infrastructure.kafka;

import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstructionPublisher;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 기반 캠페인 발송 지시 publisher 어댑터.
 *
 * <p>토픽: {@code campaign.send.requested}.
 * Partition key: projectId 문자열 — 같은 프로젝트 발송 순서 보장.
 *
 * <p>ADR-0011 producer 패턴 준수 — broker ack까지 동기 대기 후 응답.
 * publish 실패 시 트랜잭션 롤백으로 Campaign DRAFT 복원.
 */
@Component
@Profile("api")
@RequiredArgsConstructor
public class KafkaCampaignSendInstructionAdapter implements CampaignSendInstructionPublisher {

    public static final String TOPIC = "campaign.send.requested";

    private final KafkaTemplate<String, CampaignSendInstruction> kafkaTemplate;

    @Override
    public void publish(CampaignSendInstruction instruction) {
        String partitionKey = instruction.projectId().toString();
        try {
            kafkaTemplate.send(TOPIC, partitionKey, instruction).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Campaign send-instruction publish interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Campaign send-instruction publish failed", e.getCause());
        }
    }
}