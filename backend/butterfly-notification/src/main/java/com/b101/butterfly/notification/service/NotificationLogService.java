package com.b101.butterfly.notification.service;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.NotificationLogDetailResponse;
import com.b101.butterfly.notification.dto.response.NotificationLogResponse;
import com.b101.butterfly.notification.entity.NotificationLog;
import com.b101.butterfly.notification.entity.NotificationStatus;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.repository.NotificationLogRepository;
import com.b101.butterfly.notification.repository.NotificationLogRepository.CampaignNotificationLogRow;
import com.b101.butterfly.notification.repository.NotificationLogRepository.NotificationLogDetailRow;
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

        PageRequest pageable = PageRequest.of(page, size);
        Page<CampaignNotificationLogRow> logPage = notificationLogRepository.findCampaignLogRows(
                projectId,
                campaignId,
                channel != null ? channel.name() : null,
                status != null ? status.name() : null,
                pageable
        );

        return PageResponse.of(
                logPage.getContent().stream().map(NotificationLogService::toResponse).toList(),
                logPage.getNumber(),
                logPage.getSize(),
                logPage.getTotalElements()
        );
    }

    public NotificationLogDetailResponse getLogDetail(Long projectId, Long logId) {
        NotificationLogDetailRow log = notificationLogRepository.findLogDetailRow(projectId, logId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.LOG_NOT_FOUND));

        return toDetailResponse(log);
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

    private static NotificationLogResponse toResponse(CampaignNotificationLogRow row) {
        return new NotificationLogResponse(
                row.getLogId(),
                row.getCampaignId(),
                row.getUserId(),
                row.getDeviceId(),
                row.getChannel(),
                row.getStatus(),
                row.getSentAt(),
                row.getDeliveredAt(),
                row.getClickedAt()
        );
    }

    private static NotificationLogDetailResponse toDetailResponse(NotificationLogDetailRow row) {
        return new NotificationLogDetailResponse(
                row.getLogId(),
                row.getProjectId(),
                row.getCampaignId(),
                row.getUserId(),
                row.getDeviceId(),
                row.getChannel(),
                row.getRecipient(),
                row.getStatus(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getSentAt(),
                row.getDeliveredAt(),
                row.getClickedAt(),
                row.getCreatedAt()
        );
    }
}
