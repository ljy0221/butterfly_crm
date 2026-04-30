package com.b101.butterfly.delivery.entity;

public enum NotificationStatus {
    SENT,
    DELIVERED,
    CLICKED,
    FAILED;

    /**
     * 상태 전이 유효성 검증.
     * SENT -> DELIVERED, FAILED
     * DELIVERED -> CLICKED
     * CLICKED, FAILED -> 전이 불가
     */
    public boolean canTransitionTo(NotificationStatus target) {
        return switch (this) {
            case SENT -> target == DELIVERED || target == FAILED;
            case DELIVERED -> target == CLICKED;
            case CLICKED, FAILED -> false;
        };
    }
}
