package com.b101.butterfly.delivery.service;

import com.b101.butterfly.delivery.dto.request.ClickedCallbackRequest;
import com.b101.butterfly.delivery.dto.request.DeliveredCallbackRequest;
import com.b101.butterfly.delivery.dto.response.CallbackResponse;
import com.b101.butterfly.delivery.entity.CampaignStats;
import com.b101.butterfly.delivery.entity.NotificationLog;
import com.b101.butterfly.delivery.entity.NotificationStatus;
import com.b101.butterfly.delivery.exception.DeliveryErrorCode;
import com.b101.butterfly.delivery.repository.CampaignStatsRepository;
import com.b101.butterfly.delivery.repository.NotificationLogRepository;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationCallbackService {

    private final NotificationLogRepository notificationLogRepository;
    private final CampaignStatsRepository campaignStatsRepository;

    @Transactional
    public CallbackResponse markDelivered(Long logId, DeliveredCallbackRequest request) {
        NotificationLog log = notificationLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.LOG_NOT_FOUND));

        if (!log.getStatus().canTransitionTo(NotificationStatus.DELIVERED)) {
            throw new BusinessException(DeliveryErrorCode.INVALID_STATUS_TRANSITION);
        }

        LocalDateTime deliveredAt = request.deliveredAt() != null
                ? request.deliveredAt() : LocalDateTime.now();
        log.markDelivered(deliveredAt);

        campaignStatsRepository.findById(log.getCampaignId())
                .ifPresent(CampaignStats::incrementDelivered);

        return CallbackResponse.accepted();
    }

    @Transactional
    public CallbackResponse markClicked(Long logId, ClickedCallbackRequest request) {
        NotificationLog log = notificationLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.LOG_NOT_FOUND));

        if (!log.getStatus().canTransitionTo(NotificationStatus.CLICKED)) {
            throw new BusinessException(DeliveryErrorCode.INVALID_STATUS_TRANSITION);
        }

        LocalDateTime clickedAt = request.clickedAt() != null
                ? request.clickedAt() : LocalDateTime.now();
        log.markClicked(clickedAt);

        campaignStatsRepository.findById(log.getCampaignId())
                .ifPresent(CampaignStats::incrementClicked);

        return CallbackResponse.accepted();
    }
}
