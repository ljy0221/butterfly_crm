package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.lifecycle.application.CampaignService;
import com.b101.butterfly.campaign.lifecycle.application.command.CampaignCreateCommand;
import com.b101.butterfly.campaign.lifecycle.application.command.CampaignStatusTransitionCommand;
import com.b101.butterfly.campaign.lifecycle.application.command.CampaignUpdateCommand;
import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.application.CampaignActivator;
import com.b101.butterfly.campaign.lifecycle.application.CampaignCreator;
import com.b101.butterfly.campaign.lifecycle.application.CampaignModifier;
import com.b101.butterfly.campaign.lifecycle.application.CampaignRemover;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.error.BusinessException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignService — orchestration / transitionStatus 위임")
class CampaignServiceTest {

    private static final Long CAMPAIGN_ID = 100L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    private CampaignRepository campaignRepository;
    private CampaignCreator creator;
    private CampaignModifier modifier;
    private CampaignRemover remover;
    private CampaignActivator activator;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        creator = mock(CampaignCreator.class);
        modifier = mock(CampaignModifier.class);
        remover = mock(CampaignRemover.class);
        activator = mock(CampaignActivator.class);
        service = new CampaignService(campaignRepository, creator, modifier, remover, activator);
    }

    @Test
    @DisplayName("createCampaign: command를 그대로 Creator로 위임")
    void createDelegatesToCreator() {
        given(creator.createImmediateCampaign(
                eq(PROJECT_ID), eq(MEMBER_ID), eq(50L), eq(60L), eq("이름"), eq("설명")))
                .willReturn(99L);

        Long id = service.createCampaign(new CampaignCreateCommand(
                PROJECT_ID, MEMBER_ID, 50L, 60L, "이름", "설명"));

        assertThat(id).isEqualTo(99L);
    }

    @Test
    @DisplayName("updateCampaign: command를 Modifier로 위임")
    void updateDelegatesToModifier() {
        given(modifier.updateCampaign(CAMPAIGN_ID, PROJECT_ID, "n", "d")).willReturn(CAMPAIGN_ID);

        Long id = service.updateCampaign(new CampaignUpdateCommand(
                CAMPAIGN_ID, PROJECT_ID, "n", "d"));

        assertThat(id).isEqualTo(CAMPAIGN_ID);
    }

    @Test
    @DisplayName("deleteCampaign: Remover로 위임")
    void deleteDelegatesToRemover() {
        service.deleteCampaign(CAMPAIGN_ID, PROJECT_ID);
        verify(remover, times(1)).deleteCampaign(CAMPAIGN_ID, PROJECT_ID);
    }

    @Nested
    @DisplayName("transitionStatus — switch 기반 도메인 메서드 위임")
    class TransitionStatus {
        @Test
        @DisplayName("DRAFT → ACTIVE: Activator로 위임 (Execution 생성 + publish)")
        void draftToActiveDelegatesToActivator() {
            Campaign campaign = persisted(CampaignStatus.DRAFT);
            given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

            CampaignStatus result = service.transitionStatus(new CampaignStatusTransitionCommand(
                    CAMPAIGN_ID, PROJECT_ID, CampaignStatus.ACTIVE));

            assertThat(result).isEqualTo(CampaignStatus.ACTIVE);
            verify(activator, times(1)).activate(CAMPAIGN_ID, PROJECT_ID);
        }

        @Test
        @DisplayName("PAUSED → ACTIVE: Activator 호출 X (단순 resume — 도메인 메서드만)")
        void pausedToActiveSkipsActivator() {
            Campaign campaign = persisted(CampaignStatus.DRAFT);
            campaign.activate();
            campaign.pause();
            given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

            CampaignStatus result = service.transitionStatus(new CampaignStatusTransitionCommand(
                    CAMPAIGN_ID, PROJECT_ID, CampaignStatus.ACTIVE));

            assertThat(result).isEqualTo(CampaignStatus.ACTIVE);
            assertThat(campaign.getCampaignStatus()).isEqualTo(CampaignStatus.ACTIVE);
            verify(activator, never()).activate(anyLong(), anyLong());
        }

        @Test
        @DisplayName("ACTIVE → PAUSED: 도메인 pause() 호출")
        void activeToPaused() {
            Campaign campaign = persisted(CampaignStatus.DRAFT);
            campaign.activate();
            given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

            CampaignStatus result = service.transitionStatus(new CampaignStatusTransitionCommand(
                    CAMPAIGN_ID, PROJECT_ID, CampaignStatus.PAUSED));

            assertThat(result).isEqualTo(CampaignStatus.PAUSED);
            assertThat(campaign.getCampaignStatus()).isEqualTo(CampaignStatus.PAUSED);
        }

        @Test
        @DisplayName("ACTIVE → ENDED: 도메인 complete() 호출")
        void activeToEnded() {
            Campaign campaign = persisted(CampaignStatus.DRAFT);
            campaign.activate();
            given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

            CampaignStatus result = service.transitionStatus(new CampaignStatusTransitionCommand(
                    CAMPAIGN_ID, PROJECT_ID, CampaignStatus.ENDED));

            assertThat(result).isEqualTo(CampaignStatus.ENDED);
            assertThat(campaign.getCampaignStatus()).isEqualTo(CampaignStatus.ENDED);
        }

        @Test
        @DisplayName("DRAFT는 target으로 허용 안 됨 — INVALID_STATUS_TRANSITION")
        void targetDraftRejected() {
            assertThatThrownBy(() -> service.transitionStatus(
                    new CampaignStatusTransitionCommand(
                            CAMPAIGN_ID, PROJECT_ID, CampaignStatus.DRAFT)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_STATUS_TRANSITION);

            verify(activator, never()).activate(anyLong(), anyLong());
        }

        @Test
        @DisplayName("다른 프로젝트의 캠페인 전이 시 CAMPAIGN_NOT_FOUND")
        void crossProjectRejected() {
            Campaign campaign = persisted(CampaignStatus.DRAFT);
            given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

            assertThatThrownBy(() -> service.transitionStatus(
                    new CampaignStatusTransitionCommand(
                            CAMPAIGN_ID, 999L, CampaignStatus.ACTIVE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
    }

    private Campaign persisted(CampaignStatus initialStatus) {
        MessageTemplateSnapshot t =
                new MessageTemplateSnapshot(50L, PROJECT_ID, Channel.PUSH);
        Campaign c = Campaign.immediateAll(PROJECT_ID, t, MEMBER_ID, "C", "CD");
        setField(c, "campaignId", CAMPAIGN_ID);
        // initialStatus는 항상 DRAFT로 시작 — 호출자가 추가 전이로 setup
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
