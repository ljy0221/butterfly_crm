package com.b101.butterfly.campaign.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.campaign.domain.Campaign;
import com.b101.butterfly.campaign.campaign.domain.CampaignModifier;
import com.b101.butterfly.campaign.campaign.domain.CampaignRepository;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.campaign.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
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

    private CampaignRepository campaignRepository;
    private CampaignModifier modifier;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        modifier = new CampaignModifier(campaignRepository);
    }

    @Test
    @DisplayName("DRAFT 캠페인 일반 필드 수정")
    void updateDraftCampaign() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        Long id = modifier.updateCampaign(CAMPAIGN_ID, PROJECT_ID, "새이름", "새설명");

        assertThat(id).isEqualTo(CAMPAIGN_ID);
        assertThat(campaign.getCampaignName()).isEqualTo("새이름");
        assertThat(campaign.getCampaignDescription()).isEqualTo("새설명");
    }

    @Test
    @DisplayName("다른 프로젝트의 캠페인 수정 시 CAMPAIGN_NOT_FOUND")
    void crossProjectRejected() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        assertThatThrownBy(() -> modifier.updateCampaign(CAMPAIGN_ID, 999L, "x", "y"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
    }

    @Test
    @DisplayName("ACTIVE 캠페인은 수정 거부 (도메인 메서드의 NOT_MODIFIABLE)")
    void activeCampaignRejected() {
        Campaign campaign = persistedCampaign(PROJECT_ID);
        campaign.activate();
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        assertThatThrownBy(() -> modifier.updateCampaign(CAMPAIGN_ID, PROJECT_ID, "x", "y"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.NOT_MODIFIABLE);
    }

    private Campaign persistedCampaign(Long projectId) {
        MessageTemplate template = MessageTemplate.create(
                projectId, MEMBER_ID, "T", "T-desc",
                Channel.PUSH, "T-title", "T-body", null);
        Campaign c = Campaign.immediateAll(projectId, template, MEMBER_ID, "원본", "원본설명");
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