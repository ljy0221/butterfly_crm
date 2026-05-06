package com.b101.butterfly.campaign.messagetemplate.port;

import com.b101.butterfly.campaign.messagetemplate.domain.Channel;

/**
 * lifecycle BC가 messagetemplate BC 엔티티에 직접 의존하지 않도록 노출하는 read-only snapshot.
 *
 * <p>messagetemplate BC가 자기 도메인을 외부 BC에 노출할 때 사용하는 좁은 view.
 */
public record MessageTemplateSnapshot(
        Long messageTemplateId,
        Long projectId,
        Channel channel,
        String body
) {
    public MessageTemplateSnapshot(Long messageTemplateId, Long projectId, Channel channel) {
        this(messageTemplateId, projectId, channel, null);
    }
}
