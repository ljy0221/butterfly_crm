package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.lifecycle.application.CampaignCreator;
import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MemberExistencePort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
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
    private MessageTemplateLookupPort messageTemplateLookupPort;
    private ProjectExistencePort projectExistencePort;
    private MemberExistencePort memberExistencePort;
    private CampaignCreator creator;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        messageTemplateLookupPort = mock(MessageTemplateLookupPort.class);
        projectExistencePort = mock(ProjectExistencePort.class);
        memberExistencePort = mock(MemberExistencePort.class);
        creator = new CampaignCreator(
                campaignRepository,
                messageTemplateLookupPort,
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
        @DisplayName("프로젝트 없으면 CAMPAIGN_PROJECT_NOT_FOUND")
        void projectNotFound() {
            given(projectExistencePort.existsProject(PROJECT_ID)).willReturn(false);

            assertThatThrownBy(() -> creator.createImmediateCampaign(
                    PROJECT_ID, MEMBER_ID, TEMPLATE_ID, null, "이름", "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_PROJECT_NOT_FOUND);
        }

        @Test
        @DisplayName("멤버 없으면 CAMPAIGN_MEMBER_NOT_FOUND")
        void memberNotFound() {
            given(projectExistencePort.existsProject(PROJECT_ID)).willReturn(true);
            given(memberExistencePort.existsMember(MEMBER_ID)).willReturn(false);

            assertThatThrownBy(() -> creator.createImmediateCampaign(
                    PROJECT_ID, MEMBER_ID, TEMPLATE_ID, null, "이름", "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("템플릿이 다른 프로젝트 소유면 거부")
        void crossProjectTemplate() {
            given(projectExistencePort.existsProject(PROJECT_ID)).willReturn(true);
            given(memberExistencePort.existsMember(MEMBER_ID)).willReturn(true);
            given(messageTemplateLookupPort.lookup(TEMPLATE_ID))
                    .willReturn(otherProjectSnapshot());

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
        given(messageTemplateLookupPort.lookup(TEMPLATE_ID))
                .willReturn(snapshot(PROJECT_ID));
    }

    private MessageTemplateSnapshot snapshot(Long projectId) {
        return new MessageTemplateSnapshot(TEMPLATE_ID, projectId, Channel.PUSH);
    }

    private MessageTemplateSnapshot otherProjectSnapshot() {
        return snapshot(999L);
    }
}
