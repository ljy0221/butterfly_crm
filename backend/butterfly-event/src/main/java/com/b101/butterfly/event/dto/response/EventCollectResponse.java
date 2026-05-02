package com.b101.butterfly.event.dto.response;

import java.time.LocalDateTime;

public record EventCollectResponse(
        String eventId,
        LocalDateTime acceptedAt,
        Integer schemaVersion
) {
}
