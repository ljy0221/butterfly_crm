package com.b101.butterfly.delivery.service;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;

/**
 * 발송 오케스트레이션 서비스.
 * 채널 어댑터를 통해 메시지를 발송하고, 결과를 로그/통계에 반영한다.
 */
public interface DeliveryService {

    void deliver(DeliveryCommand command);
}
