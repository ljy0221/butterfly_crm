package com.b101.butterfly.campaign.campaign.domain.port;

/**
 * 캠페인 발송 지시(Send Instruction)를 외부 메시지 인프라로 발행하는 out port.
 *
 * <p>Campaign 도메인은 발송 인프라(Kafka 등)를 직접 알지 않고 본 인터페이스만 의존한다.
 * 구현체는 {@code infrastructure/kafka} 패키지에 위치한다 (Phase 3에서 연결).
 */
public interface CampaignSendInstructionPublisher {

    void publish(CampaignSendInstruction instruction);
}
