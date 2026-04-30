package com.b101.butterfly.customer.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventCollectRequest(
        @NotNull Long userId,
        @NotNull Long deviceId,
        @NotBlank String eventCategory,
        @NotNull LocalDateTime occurredAt,
        JsonNode eventData
) {
}
