package com.b101.butterfly.notification.controller;

import com.b101.butterfly.notification.entity.NotificationLog;
import com.b101.butterfly.notification.entity.NotificationStatus;
import com.b101.butterfly.notification.repository.NotificationLogRepository;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("api")
@RequestMapping("/api/v1/devices/{deviceId}")
@RequiredArgsConstructor
public class PendingNotificationController {

    private final NotificationLogRepository notificationLogRepository;

    record PendingNotification(String title, String body, String clickUrl) {}

    @GetMapping("/pending-notifications")
    @Transactional
    public ApiResponse<List<PendingNotification>> getPending(@PathVariable Long deviceId) {
        List<NotificationLog> logs = notificationLogRepository
                .findByDeviceIdAndStatus(deviceId, NotificationStatus.SENT);

        if (!logs.isEmpty()) {
            notificationLogRepository.markDeliveredByDeviceId(deviceId);
        }

        List<PendingNotification> result = logs.stream()
                .map(l -> new PendingNotification(l.getTitle(), l.getBody(), l.getClickUrl()))
                .toList();

        return ApiResponse.ok(result);
    }
}
