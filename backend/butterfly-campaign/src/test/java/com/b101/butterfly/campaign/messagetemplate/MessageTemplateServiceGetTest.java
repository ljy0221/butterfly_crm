package com.b101.butterfly.campaign.messagetemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.messagetemplate.application.MessageTemplateService;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateCreator;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateModifier;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRemover;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRepository;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MemberInformationPort;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectInformationPort;
import com.b101.butterfly.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MessageTemplateService.getMessageTemplate() — IDOR 방지")
class MessageTemplateServiceGetTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long OTHER_PROJECT_ID = 2L;
    private static final Long TEMPLATE_ID = 100L;
    private static final Long MEMBER_ID = 10L;

    private MessageTemplateRepository messageTemplateRepository;
    private MemberInformationPort memberInformationPort;
    private ProjectInformationPort projectInformationPort;
    private MessageTemplateService service;

    @BeforeEach
    void setUp() {
        messageTemplateRepository = mock(MessageTemplateRepository.class);
        memberInformationPort = mock(MemberInformationPort.class);
        projectInformationPort = mock(ProjectInformationPort.class);
        service = new MessageTemplateService(
                mock(MessageTemplateCreator.class),
                messageTemplateRepository,
                memberInformationPort,
                projectInformationPort,
                mock(MessageTemplateModifier.class),
                mock(MessageTemplateRemover.class)
        );
    }

    @Test
    @DisplayName("IDOR: 다른 프로젝트 소유 템플릿 조회 시 TEMPLATE_NOT_FOUND (존재 여부 노출 X)")
    void crossProjectAccessRejected() {
        // Given: templateId=100 은 projectId=1 소유
        MessageTemplate template = MessageTemplate.create(
                PROJECT_ID, MEMBER_ID,
                "이름", "설명", Channel.PUSH, "제목", "바디", null);
        given(messageTemplateRepository.getByMessageTemplateId(TEMPLATE_ID))
                .willReturn(template);

        // When: projectId=2 가 templateId=100 조회 시도
        // Then: 존재하지 않는 것처럼 TEMPLATE_NOT_FOUND
        assertThatThrownBy(() -> service.getMessageTemplate(OTHER_PROJECT_ID, TEMPLATE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND);
    }
}
