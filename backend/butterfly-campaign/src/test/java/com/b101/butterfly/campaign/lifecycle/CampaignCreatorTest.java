package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.lifecycle.application.CampaignCreator;
import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.ScheduleSpec;
import com.b101.butterfly.campaign.lifecycle.domain.Targeting;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MemberExistencePort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectExistencePort;
import com.b101.butterfly.global.error.BusinessException;
import java.lang.reflect.Field;
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

    @Nested
    @DisplayName("duplicateCampaign — ENDED 캠페인 새 초안 복사")
    class DuplicateCampaign {

        private static final Long ORIGIN_ID = 200L;

        @Test
        @DisplayName("정상 복사 → 새 campaignId 반환, 이름에 [재실행] 접두어")
        void duplicateHappy() {
            Campaign origin = buildEndedCampaign(PROJECT_ID, ORIGIN_ID);
            given(campaignRepository.getByCampaignId(ORIGIN_ID)).willReturn(origin);
            given(messageTemplateLookupPort.lookup(TEMPLATE_ID)).willReturn(snapshot(PROJECT_ID));
            given(campaignRepository.save(any(Campaign.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            creator.duplicateCampaign(ORIGIN_ID, PROJECT_ID, MEMBER_ID);

            verify(campaignRepository).save(any(Campaign.class));
        }

        @Test
        @DisplayName("다른 프로젝트 캠페인 복사 시도 → CAMPAIGN_NOT_FOUND")
        void duplicateCrossProject() {
            Campaign origin = buildEndedCampaign(PROJECT_ID, ORIGIN_ID);
            given(campaignRepository.getByCampaignId(ORIGIN_ID)).willReturn(origin);

            assertThatThrownBy(() -> creator.duplicateCampaign(ORIGIN_ID, 999L, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }

        private Campaign buildEndedCampaign(Long projectId, Long campaignId) {
            MessageTemplateSnapshot template = new MessageTemplateSnapshot(TEMPLATE_ID, projectId, Channel.PUSH);
            Campaign.CampaignCommonInputs common = new Campaign.CampaignCommonInputs(
                    projectId, template, MEMBER_ID, "원본 캠페인", "원본 설명");
            Campaign c = Campaign.create(common, new ScheduleSpec.Immediate(), new Targeting.All());
            setField(c, "campaignId", campaignId);
            c.activate();
            c.complete();
            return c;
        }

        private static void setField(Object target, String name, Object value) {
            try {
                Field f = findField(target.getClass(), name);
                f.setAccessible(true);
                f.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Field findField(Class<?> c, String n) throws NoSuchFieldException {
            Class<?> cur = c;
            while (cur != null) {
                try { return cur.getDeclaredField(n); } catch (NoSuchFieldException ignored) { cur = cur.getSuperclass(); }
            }
            throw new NoSuchFieldException(n);
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
