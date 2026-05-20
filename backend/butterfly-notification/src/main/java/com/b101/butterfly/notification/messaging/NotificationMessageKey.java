package com.b101.butterfly.notification.messaging;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;

public final class NotificationMessageKey {

    private NotificationMessageKey() {
    }

    public static String of(DeliveryCommand command, DeliveryCommand.DeliveryTarget target) {
        if (command.executionId() == null || target.userId() == null || command.channel() == null) {
            return null;
        }

        String devicePart = target.deviceId() != null ? target.deviceId().toString() : "none";
        return command.executionId() + ":" + target.userId() + ":" + devicePart + ":" + command.channel();
    }
}
