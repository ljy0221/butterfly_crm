package com.b101.butterfly.delivery.service.email;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.DeliveryResult;
import com.b101.butterfly.delivery.service.ChannelAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelAdapter implements ChannelAdapter {

    private final EmailService emailService;

    @Override
    public DeliveryCommand.Channel supportedChannel() {
        return DeliveryCommand.Channel.EMAIL;
    }

    @Override
    public DeliveryResult send(DeliveryCommand command, DeliveryCommand.DeliveryTarget target) {
        return emailService.sendOne(command.message(), target);
    }
}
