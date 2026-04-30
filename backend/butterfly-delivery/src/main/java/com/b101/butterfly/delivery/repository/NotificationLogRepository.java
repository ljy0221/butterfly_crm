package com.b101.butterfly.delivery.repository;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.entity.NotificationLog;
import com.b101.butterfly.delivery.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByProjectIdAndCampaignId(
            Long projectId, Long campaignId, Pageable pageable);

    Page<NotificationLog> findByProjectIdAndCampaignIdAndStatus(
            Long projectId, Long campaignId, NotificationStatus status, Pageable pageable);

    Page<NotificationLog> findByProjectIdAndCampaignIdAndChannel(
            Long projectId, Long campaignId, DeliveryCommand.Channel channel, Pageable pageable);

    Page<NotificationLog> findByProjectIdAndCampaignIdAndChannelAndStatus(
            Long projectId, Long campaignId, DeliveryCommand.Channel channel,
            NotificationStatus status, Pageable pageable);

    Page<NotificationLog> findByProjectIdAndUserId(
            Long projectId, Long userId, Pageable pageable);
}
