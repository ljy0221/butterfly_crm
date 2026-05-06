package com.b101.butterfly.event.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDateTime;

public record ProjectEventResponse(
        String eventId,
        String userId,
        String eventCategory,
        LocalDateTime occurredAt,
        @JsonRawValue String eventData
) {
}
