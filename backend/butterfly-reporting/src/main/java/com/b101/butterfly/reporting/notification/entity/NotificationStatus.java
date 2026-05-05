package com.b101.butterfly.reporting.notification.entity;

/**
 * notification_logs.status ENUM 값 (V1 마이그레이션 정의).
 *
 * <p>허용 전이 (S14P31B101-175):
 * <ul>
 *     <li>{@link #PENDING} → {@link #SENT}, {@link #FAILED}</li>
 *     <li>{@link #SENT} → {@link #DELIVERED}, {@link #FAILED}</li>
 *     <li>{@link #DELIVERED}, {@link #FAILED}는 종착점 (자기 자신만 멱등 허용)</li>
 *     <li>같은 status 재수신은 허용 (멱등)</li>
 * </ul>
 *
 * <p>CLICKED는 user_action_logs로 분리된다 (V1 주석 참고).
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED;

    /**
     * 현재 상태({@code this})에서 {@code target}으로 전이 가능한지 검사한다.
     * 같은 status로의 전이는 멱등이므로 항상 true다.
     */
    public boolean canTransitionTo(NotificationStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == SENT || target == FAILED;
            case SENT -> target == DELIVERED || target == FAILED;
            case DELIVERED, FAILED -> false;
        };
    }
}
