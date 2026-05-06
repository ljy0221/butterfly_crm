package com.b101.butterfly.campaign.messagetemplate.port;

/**
 * lifecycle BC가 messagetemplate BC를 조회하기 위한 좁은 진입점 (양방향 BC ACL).
 *
 * <p>messagetemplate.port에서 BC가 외부에 제공하는 자기 동기화된 read-only 인터페이스다.
 * lifecycle 도메인은 본 포트만 보고 messagetemplate 엔티티는 import하지 않는다.
 */
public interface MessageTemplateLookupPort {

    /**
     * 메세지 템플릿 ID로 snapshot을 조회한다.
     *
     * @throws com.b101.butterfly.global.error.BusinessException 존재하지 않으면
     *         {@code TEMPLATE_NOT_FOUND}
     */
    MessageTemplateSnapshot lookup(Long messageTemplateId);
}
