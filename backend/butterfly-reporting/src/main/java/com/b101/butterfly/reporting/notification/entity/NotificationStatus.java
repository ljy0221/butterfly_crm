package com.b101.butterfly.reporting.notification.entity;

/**
 * notification_logs.status ENUM 값 (V1 마이그레이션 정의).
 *
 * <p>전이 규칙은 별도 티켓 S14P31B101-175에서 검증한다 — 본 enum은 값 정의만 책임진다.
 * CLICKED는 user_action_logs로 분리된다 (V1 주석 참고).
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}
