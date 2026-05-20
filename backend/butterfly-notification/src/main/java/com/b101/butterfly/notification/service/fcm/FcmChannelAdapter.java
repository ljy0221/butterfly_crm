package com.b101.butterfly.notification.service.fcm;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.service.ChannelAdapter;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmChannelAdapter implements ChannelAdapter {

    private final FcmService fcmService;

    @Override
    public DeliveryCommand.Channel supportedChannel() {
        return DeliveryCommand.Channel.PUSH;
    }

    @Override
    public List<TargetOutcome> sendBatch(DeliveryCommand command, List<DeliveryCommand.DeliveryTarget> targets) {
        if (!(command.credential() instanceof DeliveryCommand.FcmCredential fcm)) {
            log.error("FCM credential 타입 불일치: executionId={}, credentialType={}",
                    command.executionId(),
                    command.credential() != null ? command.credential().getClass().getSimpleName() : "null");
            throw new BusinessException(DeliveryErrorCode.FCM_CREDENTIALS_NOT_FOUND);
        }
        log.info("FCM 배치 발송 시작: executionId={}, campaignId={}, targetCount={}",
                command.executionId(), command.campaignId(), targets.size());
        try {
            List<TargetOutcome> outcomes = fcmService.sendBatch(command, fcm.credentialJson(), targets);
            long sent = outcomes.stream().filter(o -> o.result().successful()).count();
            log.info("FCM 배치 발송 완료: executionId={}, sent={}, failed={}",
                    command.executionId(), sent, outcomes.size() - sent);
            return outcomes;
        } catch (BusinessException e) {
            log.warn("FCM credential 오류 — 전체 실패 처리: projectId={}, error={}",
                    command.projectId(), e.getMessage());
            return targets.stream()
                    .map(t -> new TargetOutcome(t, DeliveryResult.permanentFailure("FCM_CREDENTIALS_NOT_FOUND", e.getMessage())))
                    .toList();
        }
    }
}
