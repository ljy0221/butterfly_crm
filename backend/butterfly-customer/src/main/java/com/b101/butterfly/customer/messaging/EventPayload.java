package com.b101.butterfly.customer.messaging;

import java.time.LocalDateTime;

public record EventPayload(
        String eventUuid,
        Long projectId,
        Long userId,
        Long deviceId,
        Long categoryId,
        LocalDateTime occurredAt,
        Integer schemaVersion,
        String eventData
) {
}
