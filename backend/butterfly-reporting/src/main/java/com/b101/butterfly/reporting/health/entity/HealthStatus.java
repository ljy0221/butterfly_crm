package com.b101.butterfly.reporting.health.entity;

import java.math.BigDecimal;

/**
 * 시스템 헬스 메트릭의 상태 분류 — V2 마이그레이션의 ENUM('OK','WARN','CRITICAL') 매핑.
 *
 * <p>{@link #from(BigDecimal, BigDecimal, BigDecimal)} 헬퍼는 임계치 정책의 가장 흔한 형태
 * (값이 클수록 나쁘다 — 예: 누적 불일치 카운트, 지연 시간) 에 맞춰 상태를 도출한다. 값이 작을수록
 * 나쁜 메트릭(예: TPS, 성공률)은 호출자가 직접 status 를 결정해 빌더에 넘긴다.
 */
public enum HealthStatus {

    OK,
    WARN,
    CRITICAL;

    /**
     * 값이 클수록 나쁜 메트릭의 상태 분류.
     *
     * <ul>
     *   <li>warn / crit null 이면 OK 강제 (임계치 미설정)</li>
     *   <li>value &gt;= crit → CRITICAL</li>
     *   <li>value &gt;= warn → WARN</li>
     *   <li>나머지 → OK</li>
     * </ul>
     */
    public static HealthStatus from(BigDecimal value, BigDecimal warn, BigDecimal crit) {
        if (value == null) return OK;
        if (crit != null && value.compareTo(crit) >= 0) return CRITICAL;
        if (warn != null && value.compareTo(warn) >= 0) return WARN;
        return OK;
    }
}
