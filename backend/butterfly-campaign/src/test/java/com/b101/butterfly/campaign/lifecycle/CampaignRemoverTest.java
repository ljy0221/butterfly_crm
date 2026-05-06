package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.application.CampaignRemover;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.lifecycle.domain.ScheduleSpec;
import com.b101.butterfly.campaign.lifecycle.domain.Targeting;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.error.BusinessException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignRemover")
class CampaignRemoverTest {

    private static final Long CAMPAIGN_ID = 200L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    private CampaignRepository campaignRepository;
    private CampaignRemover remover;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        remover = new CampaignRemover(campaignRepository);
    }

    @Test
    @DisplayName("DRAFT 캠페인 soft delete")
    void deleteDraft() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        remover.deleteCampaign(CAMPAIGN_ID, PROJECT_ID);

        verify(campaignRepository, times(1)).delete(campaign);
    }

    @Test
    @DisplayName("다른 프로젝트의 캠페인 삭제 시 CAMPAIGN_NOT_FOUND")
    void crossProjectRejected() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        assertThatThrownBy(() -> remover.deleteCampaign(CAMPAIGN_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);

        verify(campaignRepository, never()).delete(any(Campaign.class));
    }

    @Test
    @DisplayName("ACTIVE 캠페인은 삭제 거부 (NOT_MODIFIABLE)")
    void activeCampaignRejected() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        campaign.activate();
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        assertThatThrownBy(() -> remover.deleteCampaign(CAMPAIGN_ID, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.NOT_MODIFIABLE);

        verify(campaignRepository, never()).delete(any(Campaign.class));
    }

    private Campaign persistedCampaign(Long projectId) {
        MessageTemplateSnapshot template =
                new MessageTemplateSnapshot(50L, projectId, Channel.PUSH);
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