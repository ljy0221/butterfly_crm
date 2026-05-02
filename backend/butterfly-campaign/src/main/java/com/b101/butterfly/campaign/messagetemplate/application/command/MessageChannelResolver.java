package com.b101.butterfly.campaign.messagetemplate.application.command;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;

/**
 * 외부 입력 문자열을 도메인 {@link Channel}로 안전하게 변환한다. 잘못된 enum 값에서 발생하는 {@link IllegalArgumentException}을
 * 비즈니스 예외(400)로 매핑한다.
 */
public final class MessageChannelResolver {

    private MessageChannelResolver() {
    }

    public static Channel resolve(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_CHANNEL_REQUIRED);
        }
        try {
            return Channel.valueOf(channel);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_CHANNEL_INVALID);
        }
    }
}
