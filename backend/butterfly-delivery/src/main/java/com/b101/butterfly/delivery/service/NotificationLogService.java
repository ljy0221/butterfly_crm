package com.b101.butterfly.delivery.service;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.NotificationLogDetailResponse;
import com.b101.butterfly.delivery.dto.response.NotificationLogResponse;
import com.b101.butterfly.delivery.entity.NotificationLog;
import com.b101.butterfly.delivery.entity.NotificationStatus;
import com.b101.butterfly.delivery.exception.DeliveryErrorCode;
import com.b101.butterfly.delivery.repository.NotificationLogRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    public PageResponse<NotificationLogResponse> getCampaignLogs(
            Long projectId, Long campaignId,
            DeliveryCommand.Channel channel, NotificationStatus status,
            int page, int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<NotificationLog> logPage;

        if (channel != null && status != null) {
            logPage = notificationLogRepository
                    .findByProjectIdAndCampaignIdAndChannelAndStatus(
                            projectId, campaignId, channel, status, pageable);
        } else if (channel != null) {
            logPage = notificationLogRepository
                    .findByProjectIdAndCampaignIdAndChannel(
                            projectId, campaignId, channel, pageable);
        } else if (status != null) {
            logPage = notificationLogRepository
                    .findByProjectIdAndCampaignIdAndStatus(projectId, campaignId, status, pageable);
        } else {
            logPage = notificationLogRepository
                    .findByProjectIdAndCampaignId(projectId, campaignId, pageable);
        }

        return PageResponse.of(
                logPage.getContent().stream().map(NotificationLogResponse::from).toList(),
                logPage.getNumber(),
                logPage.getSize(),
                logPage.getTotalElements()
        );
    }

    public NotificationLogDetailResponse getLogDetail(Long projectId, Long logId) {
        NotificationLog log = notificationLogRepository.findById(logId)
                .filter(l -> l.getProjectId().equals(projectId))
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.LOG_NOT_FOUND));

        return NotificationLogDetailResponse.from(log);
    }

    public PageResponse<NotificationLogResponse> getUserLogs(
            Long projectId, Long userId, int page, int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<NotificationLog> logPage =
                notificationLogRepository.findByProjectIdAndUserId(projectId, userId, pageable);

        return PageResponse.of(
                logPage.getContent().stream().map(NotificationLogResponse::from).toList(),
                logPage.getNumber(),
                logPage.getSize(),
                logPage.getTotalElements()
        );
    }
}
