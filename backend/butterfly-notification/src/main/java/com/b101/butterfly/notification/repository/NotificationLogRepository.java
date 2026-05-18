package com.b101.butterfly.notification.repository;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.entity.NotificationLog;
import com.b101.butterfly.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByDeviceIdAndStatus(Long deviceId, NotificationStatus status);

    @Modifying
    @Query("UPDATE SenderNotificationLog n SET n.status = 'DELIVERED' WHERE n.deviceId = :deviceId AND n.status = 'SENT'")
    int markDeliveredByDeviceId(@Param("deviceId") Long deviceId);

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
