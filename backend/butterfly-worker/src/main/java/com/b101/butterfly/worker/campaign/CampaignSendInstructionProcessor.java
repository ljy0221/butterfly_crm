package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.port.UserContactInfo;
import com.b101.butterfly.notification.port.UserContactPort;
import com.b101.butterfly.segment.port.SegmentMemberLookupPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 캠페인 발송 지시를 DeliveryCommand로 변환하는 핵심 로직.
 *
 * <p>책임:
 * <ul>
 *   <li>templateId → MessageTemplate 조회 (title, body, imageUrl)</li>
 *   <li>segmentId → 활성 사용자 목록 조회</li>
 *   <li>사용자 연락처 정보 조회 (pushToken, email 등)</li>
 *   <li>DeliveryCommand 조립 및 Kafka 발행</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSendInstructionProcessor {

    private final MessageTemplateLookupPort messageTemplateLookupPort;
    private final SegmentMemberLookupPort segmentMemberLookupPort;
    private final UserContactPort userContactPort;
    private final KafkaTemplate<String, DeliveryCommand> deliveryCommandKafkaTemplate;
    private final CampaignExecutionResultRecorder campaignExecutionResultRecorder;

    private static final String DELIVERY_REQUESTS_TOPIC = "delivery-requests";

    public void process(CampaignSendInstruction instruction) {
        // 1. 템플릿 조회
        MessageTemplateSnapshot template = messageTemplateLookupPort.lookup(instruction.templateId());
        log.debug("템플릿 조회 완료: templateId={}, title={}",
                instruction.templateId(), template.title());

        // 2. 수신자 목록 조회
        List<Long> userIds = segmentMemberLookupPort.getActiveUserIds(instruction.segmentId());
        log.debug("세그먼트 멤버 조회 완료: segmentId={}, userCount={}",
                instruction.segmentId(), userIds.size());

        if (userIds.isEmpty()) {
            log.warn("발송 대상이 없습니다: executionId={}, segmentId={}",
                    instruction.executionId(), instruction.segmentId());
            // segment empty → 발송 시도 0건 → 사양상 FAIL (sent_count == 0).
            // Kafka publish 를 거치지 않고 직접 Recorder 호출 — notification 측 publisher 가
            // 호출될 일이 없는 분기이므로 worker 가 종결 책임.
            campaignExecutionResultRecorder.recordFailure(instruction.executionId());
            return;
        }

        // 3. 사용자 연락처 정보 조회 (pushToken, email 등)
        DeliveryCommand.Channel deliveryChannel = mapChannel(instruction.channel());
        List<UserContactInfo> contacts = userContactPort.getContactsByUserIds(
                instruction.projectId(),
                userIds,
                deliveryChannel
        );

        // 4. DeliveryCommand 조립
        DeliveryCommand deliveryCommand = new DeliveryCommand(
                instruction.projectId(),
                instruction.campaignId(),
                instruction.executionId(),
                deliveryChannel,
                new DeliveryCommand.MessagePayload(
                        template.title(),
                        template.body(),
                        template.imageUrl(),
                        null,  // clickAction
                        null   // data
                ),
                userIds,
                null,  // credential (DeliveryService가 자체 조회)
                mapToDeliveryTargets(contacts)
        );

        // 5. delivery-requests 토픽으로 발행
        deliveryCommandKafkaTemplate.send(
                DELIVERY_REQUESTS_TOPIC,
                instruction.projectId().toString(),  // partition key
                deliveryCommand
        );

        log.info("DeliveryCommand 발행 완료: topic={}, campaignId={}, userCount={}",
                DELIVERY_REQUESTS_TOPIC, instruction.campaignId(), userIds.size());
    }

    private DeliveryCommand.Channel mapChannel(Channel channel) {
        return switch (channel) {
            case PUSH -> DeliveryCommand.Channel.PUSH;
            case EMAIL -> DeliveryCommand.Channel.EMAIL;
            case SMS -> DeliveryCommand.Channel.SMS;
            case WEBHOOK -> DeliveryCommand.Channel.WEBHOOK;
            case KAKAO -> DeliveryCommand.Channel.KAKAO;
        };
    }

    private List<DeliveryCommand.DeliveryTarget> mapToDeliveryTargets(List<UserContactInfo> contacts) {
        return contacts.stream()
                .map(contact -> new DeliveryCommand.DeliveryTarget(
                        contact.userId(),
                        contact.deviceId(),
                        contact.pushToken(),
                        contact.email(),
                        contact.phoneNumber()
                ))
                .toList();
    }
}
