package com.b101.butterfly.notification.service.sms;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
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
public class SmsChannelAdapter implements ChannelAdapter {

    private final SmsService smsService;

    @Override
    public DeliveryCommand.Channel supportedChannel() {
        return DeliveryCommand.Channel.SMS;
    }

    @Override
    public List<TargetOutcome> sendBatch(DeliveryCommand command, List<DeliveryCommand.DeliveryTarget> targets) {
        if (!(command.credential() instanceof DeliveryCommand.SmsCredential sms)) {
            throw new BusinessException(DeliveryErrorCode.SMS_SEND_FAILED,
                    "SMS credential is required");
        }
        return smsService.sendBatch(sms, command.message(), targets);
    }
}
