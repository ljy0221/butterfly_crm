package com.b101.butterfly.auth.constant;

/**
 * audit_logs.action 컬럼에 저장되는 상수값.
 * DB Enum 미사용 — 도메인 추가 시 마이그레이션 불필요.
 *
 * <p>역할 1(Auth) 발생 액션만 정의. 역할 4(Campaign)의 CAMPAIGN_* 액션은
 * butterfly-campaign 모듈에서 이 클래스를 참조하거나 별도 상수로 정의.
 */
public final class AuditAction {

    // ── API 키 ─────────────────────────────────────────────
    public static final String API_KEY_ISSUED  = "API_KEY_ISSUED";
    public static final String API_KEY_REVOKED = "API_KEY_REVOKED";

    // ── FCM 자격증명 ────────────────────────────────────────
    public static final String FCM_CREDENTIAL_REGISTERED = "FCM_CREDENTIAL_REGISTERED";
    public static final String FCM_CREDENTIAL_UPDATED    = "FCM_CREDENTIAL_UPDATED";
    public static final String FCM_CREDENTIAL_VIEWED     = "FCM_CREDENTIAL_VIEWED";

    // ── 멤버 ───────────────────────────────────────────────
    public static final String MEMBER_ROLE_CHANGED = "MEMBER_ROLE_CHANGED";
    public static final String MEMBER_REMOVED      = "MEMBER_REMOVED";

    private AuditAction() {}
}
