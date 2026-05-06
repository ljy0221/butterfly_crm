package com.b101.butterfly.campaign.messagetemplate.port;

/**
 * 메세지 템플릿이 ACTIVE 캠페인에 사용 중인지를 검증하기 위한 포트.
 *
 * <p>lifecycle BC가 본 인터페이스를 구현 — messagetemplate은 lifecycle 도메인을 직접 import 하지 않는다 (양방향 BC ACL).
 * "query"가 아니라 "assertion / guard" 의도이므로 이름을 {@code Guard}로 명시.
 */
public interface MessageTemplateUsageGuardPort {

    /**
     * 메세지 템플릿이 활성(ACTIVE) 캠페인에 사용 중이면 예외를 던진다.
     */
    void assertNotInUseByActiveCampaign(Long messageTemplateId);
}
