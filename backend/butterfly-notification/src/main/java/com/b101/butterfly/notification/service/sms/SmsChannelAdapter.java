package com.b101.butterfly.notification.service.sms;

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
public class SmsChannelAdapter implements ChannelAdapter {

    private final SmsService smsService;

    @Override
    public DeliveryCommand.Channel supportedChannel() {
        return DeliveryCommand.Channel.SMS;
    }

    @Override
    public DeliveryResult send(DeliveryCommand command, DeliveryCommand.DeliveryTarget target) {
        if (!(command.credential() instanceof DeliveryCommand.SmsCredential sms)) {
            throw new BusinessException(DeliveryErrorCode.SMS_SEND_FAILED,
                    "SMS credential is required");
        }
        return smsService.sendOne(sms, command.message(), target);
    }
}
