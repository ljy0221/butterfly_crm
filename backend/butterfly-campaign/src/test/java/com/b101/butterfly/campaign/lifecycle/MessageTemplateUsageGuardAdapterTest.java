package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.lifecycle.application.MessageTemplateUsageGuardAdapter;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MessageTemplateUsageGuardAdapter")
class MessageTemplateUsageGuardAdapterTest {

    private static final Long TEMPLATE_ID = 50L;

    private CampaignRepository campaignRepository;
    private MessageTemplateUsageGuardAdapter guard;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        guard = new MessageTemplateUsageGuardAdapter(campaignRepository);
    }

    @Test
    @DisplayName("ACTIVE 캠페인이 없으면 통과한다")
    void noActiveCampaignsAllow() {
        given(campaignRepository.existsByMessageTemplateIdAndCampaignStatusIn(
                eq(TEMPLATE_ID), any())).willReturn(false);

        assertThatCode(() -> guard.assertNotInUseByActiveCampaign(TEMPLATE_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACTIVE 캠페인이 하나라도 있으면 CAMPAIGN_IS_ACTIVE 예외를 던진다")
    void activeCampaignThrows() {
        given(campaignRepository.existsByMessageTemplateIdAndCampaignStatusIn(
                eq(TEMPLATE_ID), eq(List.of(CampaignStatus.ACTIVE)))).willReturn(true);

        assertThatThrownBy(() -> guard.assertNotInUseByActiveCampaign(TEMPLATE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.CAMPAIGN_IS_ACTIVE);
    }
}
