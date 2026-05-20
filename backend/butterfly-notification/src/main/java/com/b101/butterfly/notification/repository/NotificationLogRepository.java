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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Query(value = """
            SELECT
                n.log_id AS logId,
                n.campaign_id AS campaignId,
                n.user_id AS userId,
                n.device_id AS deviceId,
                n.channel AS channel,
                CASE
                    WHEN clicks.clicked_at IS NOT NULL THEN 'CLICKED'
                    WHEN n.status IN ('SENT', 'DELIVERED') THEN 'DELIVERED'
                    ELSE n.status
                END AS status,
                n.sent_at AS sentAt,
                COALESCE(n.delivered_at,
                    CASE WHEN n.status IN ('SENT', 'DELIVERED') THEN n.sent_at ELSE NULL END
                ) AS deliveredAt,
                clicks.clicked_at AS clickedAt
            FROM notification_logs n
            LEFT JOIN (
                SELECT log_id, MIN(action_at) AS clicked_at
                FROM user_action_logs
                WHERE action_type = 'CLICK'
                GROUP BY log_id
            ) clicks ON clicks.log_id = n.log_id
            WHERE n.project_id = :projectId
              AND n.campaign_id = :campaignId
              AND (:channel IS NULL OR n.channel = :channel)
              AND (
                    :status IS NULL
                 OR (:status = 'CLICKED' AND clicks.clicked_at IS NOT NULL)
                 OR (:status = 'FAILED' AND n.status = 'FAILED')
                 OR (:status IN ('SENT', 'DELIVERED') AND n.status IN ('SENT', 'DELIVERED'))
              )
            ORDER BY n.sent_at DESC, n.log_id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM notification_logs n
            LEFT JOIN (
                SELECT log_id, MIN(action_at) AS clicked_at
                FROM user_action_logs
                WHERE action_type = 'CLICK'
                GROUP BY log_id
            ) clicks ON clicks.log_id = n.log_id
            WHERE n.project_id = :projectId
              AND n.campaign_id = :campaignId
              AND (:channel IS NULL OR n.channel = :channel)
              AND (
                    :status IS NULL
                 OR (:status = 'CLICKED' AND clicks.clicked_at IS NOT NULL)
                 OR (:status = 'FAILED' AND n.status = 'FAILED')
                 OR (:status IN ('SENT', 'DELIVERED') AND n.status IN ('SENT', 'DELIVERED'))
              )
            """,
            nativeQuery = true)
    Page<CampaignNotificationLogRow> findCampaignLogRows(
            @Param("projectId") Long projectId,
            @Param("campaignId") Long campaignId,
            @Param("channel") String channel,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = """
            SELECT
                n.log_id AS logId,
                n.project_id AS projectId,
                n.campaign_id AS campaignId,
                n.user_id AS userId,
                n.device_id AS deviceId,
                n.channel AS channel,
                n.recipient AS recipient,
                CASE
                    WHEN clicks.clicked_at IS NOT NULL THEN 'CLICKED'
                    WHEN n.status IN ('SENT', 'DELIVERED') THEN 'DELIVERED'
                    ELSE n.status
                END AS status,
                n.error_code AS errorCode,
                n.error_message AS errorMessage,
                n.sent_at AS sentAt,
                COALESCE(n.delivered_at,
                    CASE WHEN n.status IN ('SENT', 'DELIVERED') THEN n.sent_at ELSE NULL END
                ) AS deliveredAt,
                clicks.clicked_at AS clickedAt,
                n.created_at AS createdAt
            FROM notification_logs n
            LEFT JOIN (
                SELECT log_id, MIN(action_at) AS clicked_at
                FROM user_action_logs
                WHERE action_type = 'CLICK'
                GROUP BY log_id
            ) clicks ON clicks.log_id = n.log_id
            WHERE n.project_id = :projectId
              AND n.log_id = :logId
            """, nativeQuery = true)
    Optional<NotificationLogDetailRow> findLogDetailRow(
            @Param("projectId") Long projectId,
            @Param("logId") Long logId
    );

    interface CampaignNotificationLogRow {
        Long getLogId();
        Long getCampaignId();
        Long getUserId();
        Long getDeviceId();
        String getChannel();
        String getStatus();
        LocalDateTime getSentAt();
        LocalDateTime getDeliveredAt();
        LocalDateTime getClickedAt();
    }

    interface NotificationLogDetailRow extends CampaignNotificationLogRow {
        Long getProjectId();
        String getRecipient();
        String getErrorCode();
        String getErrorMessage();
        LocalDateTime getCreatedAt();
    }
}
