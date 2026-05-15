package com.b101.butterfly.reporting.audit.port;

/**
 * 감사 로그 응답에서 memberId 옆에 노출할 멤버 식별 정보 VO.
 *
 * <p>reporting 모듈은 auth 도메인 객체(Member)를 직접 참조하지 않고 본 VO로만 받는다 — ADR-0014 모듈
 * 경계 준수.
 */
public record MemberLookupView(
        Long memberId,
        String name,
        String email
) {
}
