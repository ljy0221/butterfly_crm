package com.b101.butterfly.notification.service;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.TargetOutcome;

import java.util.List;

/**
 * 채널별 발송 전략 인터페이스.
 * FCM, Email, SMS, Webhook 등 각 채널은 이 인터페이스를 구현한다.
 * <p>
 * command에서 필요한 정보(projectId, campaignId, credential, message)를
 * 각 구현체가 직접 꺼내 쓰므로, 채널별로 필요한 컨텍스트가 달라도
 * 인터페이스를 변경할 필요가 없다.
 */
public interface ChannelAdapter {

    DeliveryCommand.Channel supportedChannel();

    List<TargetOutcome> sendBatch(DeliveryCommand command, List<DeliveryCommand.DeliveryTarget> targets);
}
