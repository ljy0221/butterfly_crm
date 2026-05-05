package com.b101.butterfly.event.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventCollectRequest(
        @Size(max = 255) String userId,
        @NotBlank @Size(max = 255) String deviceId,
        @NotBlank String eventCategory,
        @NotNull LocalDateTime occurredAt,
        JsonNode eventData
) {
}
