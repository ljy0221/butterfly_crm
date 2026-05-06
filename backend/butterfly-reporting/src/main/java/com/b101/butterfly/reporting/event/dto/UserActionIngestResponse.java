package com.b101.butterfly.reporting.event.dto;

import java.time.LocalDateTime;

/**
 * `POST /api/v1/events/actions` 응답 (butterfly_api_spec_v2.md #19).
 */
public record UserActionIngestResponse(Long actionId, LocalDateTime acceptedAt) {
}
