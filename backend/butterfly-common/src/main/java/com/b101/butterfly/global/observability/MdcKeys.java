package com.b101.butterfly.global.observability;

public final class MdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String PROJECT_ID = "projectId";
    public static final String MEMBER_ID = "memberId";
    public static final String TRACE_ID = "traceId";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private MdcKeys() {
    }
}
