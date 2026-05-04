package com.b101.butterfly.reporting.notification.repository;

/**
 * {@link ReportingNotificationLogRepository#countsForReconciliation} 결과 — 회차×채널 단위
 * actual/sent/failed 합산. 각 카운트는 JPQL의 COUNT/SUM 결과(Long)를 받는다.
 *
 * <p>회차에 매칭되는 row가 0건이어도 비-null Long 0이 반환된다 (COUNT/SUM 동작).
 */
public record NotificationCountSummary(
        Long actualCount,
        Long sentCount,
        Long failedCount
) {
    public int actualAsInt() {
        return actualCount == null ? 0 : actualCount.intValue();
    }

    public int sentAsInt() {
        return sentCount == null ? 0 : sentCount.intValue();
    }

    public int failedAsInt() {
        return failedCount == null ? 0 : failedCount.intValue();
    }
}
