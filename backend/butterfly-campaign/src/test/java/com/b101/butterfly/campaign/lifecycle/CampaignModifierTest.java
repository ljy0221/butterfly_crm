package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.b101.butterfly.campaign.lifecycle.application.CampaignModifier;
import com.b101.butterfly.campaign.lifecycle.application.command.CampaignUpdateCommand;
import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.ScheduleSpec;
import com.b101.butterfly.campaign.lifecycle.domain.Targeting;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.error.BusinessException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignModifier")
class CampaignModifierTest {

    private static final Long CAMPAIGN_ID = 100L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long TEMPLATE_ID = 50L;
    private static final Long NEW_TEMPLATE_ID = 60L;

    private CampaignRepository campaignRepository;
    private MessageTemplateLookupPort messageTemplateLookupPort;
    private CampaignModifier modifier;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        messageTemplateLookupPort = mock(MessageTemplateLookupPort.class);
        modifier = new CampaignModifier(campaignRepository, messageTemplateLookupPort);
    }

    @Test
    @DisplayName("DRAFT 캠페인 일반 필드 수정 (template/segment 동일)")
    void updateDraftCampaign() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(messageTemplateLookupPort.lookup(TEMPLATE_ID))
                .willReturn(new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH));

        Long id = modifier.updateCampaign(
                new CampaignUpdateCommand(CAMPAIGN_ID, PROJECT_ID, "새이름", "새설명", TEMPLATE_ID, null));

        assertThat(id).isEqualTo(CAMPAIGN_ID);
        assertThat(campaign.getCampaignName()).isEqualTo("새이름");
        assertThat(campaign.getCampaignDescription()).isEqualTo("새설명");
    }

    @Test
    @DisplayName("messageTemplateId 변경 시 channel 도 새 template 의 channel 로 갱신")
    void updateChangesTemplateAndRecomputesChannel() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(messageTemplateLookupPort.lookup(NEW_TEMPLATE_ID))
                .willReturn(new MessageTemplateSnapshot(NEW_TEMPLATE_ID, PROJECT_ID, Channel.EMAIL));

        modifier.updateCampaign(
                new CampaignUpdateCommand(CAMPAIGN_ID, PROJECT_ID, "이름", "설명", NEW_TEMPLATE_ID, null));

        assertThat(campaign.getMessageTemplateId()).isEqualTo(NEW_TEMPLATE_ID);
        assertThat(campaign.getChannel()).isEqualTo(Channel.EMAIL);
    }

    @Test
    @DisplayName("segmentId 를 변경 가능 (null → 값)")
    void updateSetsSegmentId() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(messageTemplateLookupPort.lookup(TEMPLATE_ID))
                .willReturn(new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH));

        modifier.updateCampaign(
                new CampaignUpdateCommand(CAMPAIGN_ID, PROJECT_ID, "이름", "설명", TEMPLATE_ID, 42L));

        assertThat(campaign.getSegmentId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("다른 프로젝트의 캠페인 수정 시 CAMPAIGN_NOT_FOUND (lookup 호출 안 됨)")
    void crossProjectRejected() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        assertThatThrownBy(() -> modifier.updateCampaign(
                        new CampaignUpdateCommand(CAMPAIGN_ID, 999L, "x", "y", TEMPLATE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);

        verifyNoInteractions(messageTemplateLookupPort);
    }

    @Test
    @DisplayName("새 template 이 다른 project 소유면 CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED")
    void crossProjectTemplateRejected() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(messageTemplateLookupPort.lookup(NEW_TEMPLATE_ID))
                .willReturn(new MessageTemplateSnapshot(NEW_TEMPLATE_ID, 999L, Channel.EMAIL));

        assertThatThrownBy(() -> modifier.updateCampaign(
                        new CampaignUpdateCommand(CAMPAIGN_ID, PROJECT_ID, "이름", "설명", NEW_TEMPLATE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
    }

    @Test
    @DisplayName("ACTIVE 캠페인은 수정 거부 (도메인 메서드의 NOT_MODIFIABLE)")
    void activeCampaignRejected() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        campaign.activate();
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(messageTemplateLookupPort.lookup(TEMPLATE_ID))
                .willReturn(new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH));

        assertThatThrownBy(() -> modifier.updateCampaign(
                        new CampaignUpdateCommand(CAMPAIGN_ID, PROJECT_ID, "x", "y", TEMPLATE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.NOT_MODIFIABLE);
    }

    private Campaign persistedCampaign(Long projectId) {
        MessageTemplateSnapshot template =
                new MessageTemplateSnapshot(TEMPLATE_ID, projectId, Channel.PUSH);
        Campaign.CampaignCommonInputs common = new Campaign.CampaignCommonInputs(
                projectId, template, MEMBER_ID, "원본", "원본설명");
        Campaign c = Campaign.create(common, new ScheduleSpec.Immediate(), new Targeting.All());
        setField(c, "campaignId", CAMPAIGN_ID);
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
