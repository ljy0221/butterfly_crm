package com.b101.butterfly.worker.campaign;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.event.port.UserTagLookupPort;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.port.UserContactInfo;
import com.b101.butterfly.notification.port.UserContactPort;
import com.b101.butterfly.payment.application.CreditAccountService;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import com.b101.butterfly.segment.port.SegmentAudienceTarget;
import com.b101.butterfly.segment.port.SegmentMemberLookupPort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 캠페인 발송 지시를 DeliveryCommand로 변환하고, 발송 전 크레딧을 예약한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSendInstructionProcessor {

    private static final String DELIVERY_REQUESTS_TOPIC = "delivery-requests";

    private final MessageTemplateLookupPort messageTemplateLookupPort;
    private final SegmentMemberLookupPort segmentMemberLookupPort;
    private final UserContactPort userContactPort;
    private final UserTagLookupPort userTagLookupPort;
    private final KafkaTemplate<String, DeliveryCommand> deliveryCommandKafkaTemplate;
    private final CampaignExecutionResultRecorder campaignExecutionResultRecorder;
    private final CreditAccountService creditAccountService;

    public void process(CampaignSendInstruction instruction) {
        MessageTemplateSnapshot template = messageTemplateLookupPort.lookup(instruction.templateId());
        log.debug("템플릿 조회 완료: templateId={}, title={}",
                instruction.templateId(), template.title());

        List<SegmentAudienceTarget> audienceTargets =
                segmentMemberLookupPort.getActiveTargets(
                        instruction.segmentId(),
                        null,
                        Integer.MAX_VALUE);
        List<Long> userIds = audienceTargets.stream()
                .map(SegmentAudienceTarget::userId)
                .toList();
        log.debug("세그먼트 멤버 조회 완료: segmentId={}, userCount={}",
                instruction.segmentId(), userIds.size());

        if (userIds.isEmpty()) {
            if (instruction.segmentId() == null) {
                log.warn("segmentId가 null — 세그먼트 없는 발송은 현재 미지원: executionId={}",
                        instruction.executionId());
            } else {
                log.warn("발송 대상이 없습니다: executionId={}, segmentId={}",
                        instruction.executionId(), instruction.segmentId());
            }
            campaignExecutionResultRecorder.recordFailure(instruction.executionId());
            return;
        }

        DeliveryCommand.Channel deliveryChannel = mapChannel(instruction.channel());
        List<UserContactInfo> contacts = userContactPort.getContactsByUserIds(
                instruction.projectId(),
                userIds,
                deliveryChannel
        );
        if (contacts.isEmpty()) {
            log.warn("발송 가능한 연락처가 없습니다: executionId={}, segmentId={}",
                    instruction.executionId(), instruction.segmentId());
            campaignExecutionResultRecorder.recordFailure(instruction.executionId());
            return;
        }

        Map<Long, Map<String, String>> variablesByUser = resolveVariablesByUser(
                instruction.projectId(),
                userIds,
                audienceTargets);

        DeliveryCommand deliveryCommand = new DeliveryCommand(
                instruction.projectId(),
                instruction.campaignId(),
                instruction.executionId(),
                deliveryChannel,
                new DeliveryCommand.MessagePayload(
                        template.title(),
                        template.body(),
                        template.imageUrl(),
                        null
                ),
                userIds,
                null,
                mapToDeliveryTargets(contacts, variablesByUser)
        );

        if (!reserveCredits(instruction, deliveryCommand, deliveryChannel)) {
            return;
        }

        deliveryCommandKafkaTemplate.send(
                DELIVERY_REQUESTS_TOPIC,
                instruction.projectId().toString(),
                deliveryCommand
        );

        log.info("DeliveryCommand 발행 완료: topic={}, campaignId={}, targetCount={}",
                DELIVERY_REQUESTS_TOPIC, instruction.campaignId(), deliveryCommand.targets().size());
    }

    private boolean reserveCredits(
            CampaignSendInstruction instruction,
            DeliveryCommand deliveryCommand,
            DeliveryCommand.Channel deliveryChannel
    ) {
        try {
            creditAccountService.reserveForCampaignExecution(
                    instruction.projectId(),
                    instruction.campaignId(),
                    instruction.executionId(),
                    deliveryCommand.targets().size(),
                    CampaignCreditPricePolicy.unitPriceOf(deliveryChannel)
            );
            return true;
        } catch (BusinessException e) {
            if (e.getErrorCode() == PaymentErrorCode.PAY_INSUFFICIENT_BALANCE) {
                log.warn("크레딧 부족으로 발송을 시작하지 않습니다: executionId={}, campaignId={}",
                        instruction.executionId(), instruction.campaignId());
                campaignExecutionResultRecorder.recordFailure(instruction.executionId());
                return false;
            }
            throw e;
        }
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

    private List<DeliveryCommand.DeliveryTarget> mapToDeliveryTargets(
            List<UserContactInfo> contacts,
            Map<Long, Map<String, String>> variablesByUser
    ) {
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        return contacts.stream()
                .map(contact -> {
                    Map<String, String> vars = new HashMap<>(
                            variablesByUser.getOrDefault(contact.userId(), Map.of()));
                    vars.putIfAbsent("email", contact.email() != null ? contact.email() : "");
                    vars.putIfAbsent("phone", contact.phoneNumber() != null ? contact.phoneNumber() : "");
                    vars.putIfAbsent("today", today);
                    if (contact.birthDate() != null) vars.putIfAbsent("birth_date", contact.birthDate());
                    if (contact.signedUpAt() != null) vars.putIfAbsent("signed_up_at", contact.signedUpAt());
                    log.debug("SYSTEM 변수 해석: userId={}, email={}, phone={}, birth_date={}, signed_up_at={}, today={}, resolvedKeys={}",
                            contact.userId(),
                            contact.email() != null ? "있음" : "null",
                            contact.phoneNumber() != null ? "있음" : "null",
                            contact.birthDate() != null ? contact.birthDate() : "null",
                            contact.signedUpAt() != null ? contact.signedUpAt() : "null",
                            today,
                            vars.keySet());
                    return new DeliveryCommand.DeliveryTarget(
                            contact.userId(),
                            contact.deviceId(),
                            contact.pushToken(),
                            contact.email(),
                            contact.phoneNumber(),
                            vars
                    );
                })
                .toList();
    }

    private Map<Long, Map<String, String>> resolveVariablesByUser(
            Long projectId,
            List<Long> userIds,
            List<SegmentAudienceTarget> audienceTargets
    ) {
        boolean hasSnapshotVariables =
                audienceTargets.stream().anyMatch(target -> !target.variables().isEmpty());
        if (!hasSnapshotVariables) {
            return userTagLookupPort.getTagsByUserIds(projectId, userIds);
        }
        return audienceTargets.stream()
                .collect(Collectors.toMap(
                        SegmentAudienceTarget::userId,
                        SegmentAudienceTarget::variables));
    }
}
