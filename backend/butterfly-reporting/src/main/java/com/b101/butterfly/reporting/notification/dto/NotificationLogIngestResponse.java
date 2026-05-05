package com.b101.butterfly.reporting.notification.dto;

import java.time.LocalDateTime;

/**
 * `POST /internal/notification-logs` 응답 (butterfly_api_spec_v2.md #30).
 *
 * <p>신규 INSERT는 201, 멱등 hit(이미 같은 messageKey 존재)은 200으로 분기한다 — 컨트롤러가
 * {@link NotificationLogIngestResult#deduplicated()}를 보고 status code를 결정한다.
 */
public record NotificationLogIngestResponse(Long logId, LocalDateTime createdAt) {
}
