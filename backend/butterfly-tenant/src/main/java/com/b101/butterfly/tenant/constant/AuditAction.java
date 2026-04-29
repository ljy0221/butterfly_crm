package com.b101.butterfly.tenant.constant;

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

    // ── 신청서 ─────────────────────────────────────────────
    public static final String APPLICATION_APPROVED = "APPLICATION_APPROVED";
    public static final String APPLICATION_REJECTED = "APPLICATION_REJECTED";

    private AuditAction() {}
}
