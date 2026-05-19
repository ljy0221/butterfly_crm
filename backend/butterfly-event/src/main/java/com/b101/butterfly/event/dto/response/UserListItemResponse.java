package com.b101.butterfly.event.dto.response;

import java.time.LocalDateTime;

public record UserListItemResponse(
        Long userId,
        String externalId,
        String email,
        String phone,
        Boolean pushOptIn,
        LocalDateTime signedUpAt,
        LocalDateTime createdAt,
        long totalEvents,
        LocalDateTime lastSeenAt,
        long purchaseCount
) {
}
