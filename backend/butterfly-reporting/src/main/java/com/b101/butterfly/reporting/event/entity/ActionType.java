package com.b101.butterfly.reporting.event.entity;

/**
 * user_action_logs.action_type ENUM 값 (V2_reporting_baseline 정의).
 *
 * <p>KPI 정의서(`backend/docs/operations/kpi-and-attribution.md` §1)의 사용자 반응 카운트
 * 분류와 1:1 매칭된다 — 각 action_type이 open_count/click_count/convert_count/dismiss_count
 * 합산의 분자가 된다.
 */
public enum ActionType {
    OPEN,
    CLICK,
    CONVERT,
    DISMISS
}
