package com.b101.butterfly.campaign.messagetemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.application.command.MessageChannelResolver;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MessageChannelResolver")
class MessageChannelResolverTest {

    @Test
    @DisplayName("유효한 enum 이름은 그대로 변환된다")
    void resolvesValidName() {
        assertThat(MessageChannelResolver.resolve("EMAIL")).isEqualTo(Channel.EMAIL);
        assertThat(MessageChannelResolver.resolve("SMS")).isEqualTo(Channel.SMS);
    }

    @Test
    @DisplayName("null 입력은 TEMPLATE_CHANNEL_REQUIRED 예외")
    void nullThrowsRequired() {
        assertThatThrownBy(() -> MessageChannelResolver.resolve(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MessageTemplateErrorCode.TEMPLATE_CHANNEL_REQUIRED);
    }

    @Test
    @DisplayName("공백 문자열은 TEMPLATE_CHANNEL_REQUIRED 예외")
    void blankThrowsRequired() {
        assertThatThrownBy(() -> MessageChannelResolver.resolve(" "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MessageTemplateErrorCode.TEMPLATE_CHANNEL_REQUIRED);
    }

    @Test
    @DisplayName("정의되지 않은 채널 이름은 TEMPLATE_CHANNEL_INVALID 예외")
    void unknownNameThrowsInvalid() {
        assertThatThrownBy(() -> MessageChannelResolver.resolve("FAX"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MessageTemplateErrorCode.TEMPLATE_CHANNEL_INVALID);
    }
}
