package com.b101.butterfly.notification.service.webhook;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.service.ChannelAdapter;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    public DeliveryResult send(DeliveryCommand command, DeliveryCommand.DeliveryTarget target) {
        if (!(command.credential() instanceof DeliveryCommand.WebhookCredential wh)) {
            throw new BusinessException(DeliveryErrorCode.WEBHOOK_URL_INVALID,
                    "Webhook credential is required");
        }
        return webhookService.sendOne(command.projectId(), command.campaignId(), wh, command.message(), target);
    }
}
