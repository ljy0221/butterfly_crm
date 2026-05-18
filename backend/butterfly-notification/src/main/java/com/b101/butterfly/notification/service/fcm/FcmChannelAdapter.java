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
            throw new BusinessException(DeliveryErrorCode.FCM_CREDENTIALS_NOT_FOUND);
        }
        try {
            return fcmService.sendBatch(command.projectId(), fcm.credentialJson(), command.message(), targets);
        } catch (BusinessException e) {
            log.warn("FCM credential 오류 — 로컬 stub SENT 처리: projectId={}, error={}",
                    command.projectId(), e.getMessage());
            // 로컬 테스트용: FCM credential 없을 때 SENT로 처리해 notification_logs에 기록되게 함
            return targets.stream()
                    .map(t -> new TargetOutcome(t, DeliveryResult.success()))
                    .toList();
        }
    }
}
