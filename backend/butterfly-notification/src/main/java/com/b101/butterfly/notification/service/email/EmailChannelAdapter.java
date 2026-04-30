package com.b101.butterfly.notification.service.email;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.service.ChannelAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
    public List<TargetOutcome> sendBatch(DeliveryCommand command, List<DeliveryCommand.DeliveryTarget> targets) {
        return targets.stream()
                .map(target -> new TargetOutcome(target, emailService.sendOne(command.message(), target)))
                .toList();
    }
}
