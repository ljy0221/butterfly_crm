package com.b101.butterfly.campaign.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.campaign.domain.Campaign;
import com.b101.butterfly.campaign.campaign.domain.CampaignCreator;
import com.b101.butterfly.campaign.campaign.domain.CampaignRepository;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.campaign.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRepository;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MemberExistencePort;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectExistencePort;
import com.b101.butterfly.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignCreator")
class CampaignCreatorTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long TEMPLATE_ID = 100L;

    private CampaignRepository campaignRepository;
    private MessageTemplateRepository messageTemplateRepository;
    private ProjectExistencePort projectExistencePort;
    private MemberExistencePort memberExistencePort;
    private CampaignCreator creator;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        messageTemplateRepository = mock(MessageTemplateRepository.class);
        projectExistencePort = mock(ProjectExistencePort.class);
        memberExistencePort = mock(MemberExistencePort.class);
        creator = new CampaignCreator(
                campaignRepository,
                messageTemplateRepository,
                projectExistencePort,
                memberExistencePort
        );
    }

    @Nested
    @DisplayName("정상 경로")
    class HappyPath {

        @Test
        @DisplayName("segmentId가 null이면 immediateAll 캠페인 생성")
        void allTargetCampaign() {
            stubAllExist();
            given(campaignRepository.save(any(Campaign.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Long id = creator.createImmediateCampaign(
                    PROJECT_ID, MEMBER_ID, TEMPLATE_ID, null, "이름", "설명");

            // mocked save returns same campaign; getCampaignId is null since not persisted
            // — but the call should succeed without exception
            assertThat(id).isNull();  // mocked, no real DB
        }

        @Test
        @DisplayName("segmentId가 있으면 immediateTarget 캠페인 생성")
        void targetCampaign() {
            stubAllExist();
            given(campaignRepository.save(any(Campaign.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // 정상 호출이면 예외 없이 끝남
            creator.createImmediateCampaign(
                    PROJECT_ID, MEMBER_ID, TEMPLATE_ID, 200L, "이름", "설명");
        }
    }

    @Nested
    @DisplayName("실패 경로")
    class FailurePath {

        @Test
        @DisplayName("프로젝트 없으면 PROJECT_NOT_FOUND")
        void projectNotFound() {
            given(projectExistencePort.existsProject(PROJECT_ID)).willReturn(false);

            assertThatThrownBy(() -> creator.createImmediateCampaign(
                    PROJECT_ID, MEMBER_ID, TEMPLATE_ID, null, "이름", "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.PROJECT_NOT_FOUND);
        }

        @Test
        @DisplayName("멤버 없으면 MEMBER_NOT_FOUND")
        void memberNotFound() {
            given(projectExistencePort.existsProject(PROJECT_ID)).willReturn(true);
            given(memberExistencePort.existsMember(MEMBER_ID)).willReturn(false);

            assertThatThrownBy(() -> creator.createImmediateCampaign(
                    PROJECT_ID, MEMBER_ID, TEMPLATE_ID, null, "이름", "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MessageTemplateErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("템플릿이 다른 프로젝트 소유면 거부")
        void crossProjectTemplate() {
            given(projectExistencePort.existsProject(PROJECT_ID)).willReturn(true);
            given(memberExistencePort.existsMember(MEMBER_ID)).willReturn(true);
            given(messageTemplateRepository.getByMessageTemplateId(TEMPLATE_ID))
                    .willReturn(otherProjectTemplate());

            assertThatThrownBy(() -> creator.createImmediateCampaign(
                    PROJECT_ID, MEMBER_ID, TEMPLATE_ID, null, "이름", "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
        }
    }

    private void stubAllExist() {
        given(projectExistencePort.existsProject(PROJECT_ID)).willReturn(true);
        given(memberExistencePort.existsMember(MEMBER_ID)).willReturn(true);
        given(messageTemplateRepository.getByMessageTemplateId(TEMPLATE_ID))
                .willReturn(template(PROJECT_ID));
    }

    private MessageTemplate template(Long projectId) {
        return MessageTemplate.create(
                projectId, MEMBER_ID, "T", "T-desc",
                Channel.PUSH, "T-title", "T-body", null);
    }

    private MessageTemplate otherProjectTemplate() {
        return template(999L);
    }
}