package com.b101.butterfly.event.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long userId,
        String externalId,
        Boolean pushOptIn,
        LocalDateTime signedUpAt,
        DeviceSummary device,
        Summary summary,
        List<RecentEvent> recentEvents
) {
    public record DeviceSummary(
            String platform,
            String appVersion,
            String osVersion,
            Boolean tokenValid,
            LocalDateTime lastActiveAt
    ) {}

    public record Summary(
            long totalEvents,
            LocalDateTime lastSeenAt,
            long purchaseCount
    ) {}

    public record RecentEvent(
            Long eventId,
            String eventCategory,
            LocalDateTime occurredAt,
            @JsonRawValue String eventData
    ) {}
}
