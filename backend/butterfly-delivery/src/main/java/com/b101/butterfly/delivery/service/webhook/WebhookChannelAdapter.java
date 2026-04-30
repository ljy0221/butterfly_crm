package com.b101.butterfly.delivery.service.webhook;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.TargetOutcome;
import com.b101.butterfly.delivery.exception.DeliveryErrorCode;
import com.b101.butterfly.delivery.service.ChannelAdapter;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookChannelAdapter implements ChannelAdapter {

    private final WebhookService webhookService;

    @Override
    public DeliveryCommand.Channel supportedChannel() {
        return DeliveryCommand.Channel.WEBHOOK;
    }

    @Override
    public List<TargetOutcome> sendBatch(DeliveryCommand command, List<DeliveryCommand.DeliveryTarget> targets) {
        if (!(command.credential() instanceof DeliveryCommand.WebhookCredential wh)) {
            throw new BusinessException(DeliveryErrorCode.WEBHOOK_URL_INVALID,
                    "Webhook credential is required");
        }
        return targets.stream()
                .map(target -> new TargetOutcome(target,
                        webhookService.sendOne(command.projectId(), command.campaignId(), wh, command.message(), target)))
                .toList();
    }
}
