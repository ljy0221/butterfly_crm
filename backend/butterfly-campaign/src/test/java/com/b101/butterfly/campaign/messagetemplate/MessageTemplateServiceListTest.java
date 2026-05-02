package com.b101.butterfly.campaign.messagetemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.messagetemplate.application.MessageTemplateService;
import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateListResult;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateCreator;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateModifier;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRemover;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRepository;
import com.b101.butterfly.campaign.messagetemplate.port.MemberInformationPort;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectInformationPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DisplayName("MessageTemplateService.getMessageTemplateList()")
class MessageTemplateServiceListTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    private MessageTemplateRepository messageTemplateRepository;
    private MemberInformationPort memberInformationPort;
    private MessageTemplateService service;

    @BeforeEach
    void setUp() {
        messageTemplateRepository = mock(MessageTemplateRepository.class);
        memberInformationPort = mock(MemberInformationPort.class);
        service = new MessageTemplateService(
                mock(MessageTemplateCreator.class),
                messageTemplateRepository,
                memberInformationPort,
                mock(ProjectInformationPort.class),
                mock(MessageTemplateModifier.class),
                mock(MessageTemplateRemover.class)
        );
    }

    @Test
    @DisplayName("결과가 비어 있으면 예외 없이 빈 페이지를 반환한다")
    void emptyPageReturnsEmptyResult() {
        Pageable pageable = PageRequest.of(0, 10);
        given(messageTemplateRepository.findAllByProjectId(PROJECT_ID, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<MessageTemplateListResult> result =
                service.getMessageTemplateList(PROJECT_ID, pageable);

        assertThat(result).isEmpty();
        verify(memberInformationPort, never()).getMemberNames(List.of());
    }

    @Test
    @DisplayName("결과에 템플릿이 있으면 멤버 이름과 결합해 반환한다")
    void nonEmptyPageJoinsMemberName() {
        Pageable pageable = PageRequest.of(0, 10);
        MessageTemplate template = MessageTemplate.create(
                PROJECT_ID, MEMBER_ID,
                "이름", "설명", Channel.EMAIL, "제목", "바디", null);
        given(messageTemplateRepository.findAllByProjectId(PROJECT_ID, pageable))
                .willReturn(new PageImpl<>(List.of(template), pageable, 1));
        given(memberInformationPort.getMemberNames(List.of(MEMBER_ID)))
                .willReturn(Map.of(MEMBER_ID, "홍길동"));

        Page<MessageTemplateListResult> result =
                service.getMessageTemplateList(PROJECT_ID, pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).memberName()).isEqualTo("홍길동");
    }
}
