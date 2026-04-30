package com.b101.butterfly.delivery.service.kafka;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryKafkaConsumer {

    private final DeliveryService deliveryService;

    @KafkaListener(
            topics = "delivery-requests",
            groupId = "delivery-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(DeliveryCommand command) {
        log.info("Kafka 발송 요청 수신: projectId={}, campaignId={}, channel={}, userIds={}, targets={}",
                command.projectId(), command.campaignId(), command.channel(),
                command.userIds() != null ? command.userIds().size() : 0,
                command.targets() != null ? command.targets().size() : 0);

        try {
            deliveryService.deliver(command);
            log.info("Kafka 발송 처리 완료: campaignId={}", command.campaignId());
        } catch (Exception e) {
            log.error("Kafka 발송 처리 실패: campaignId={}, error={}",
                    command.campaignId(), e.getMessage(), e);
            throw e;
        }
    }
}
