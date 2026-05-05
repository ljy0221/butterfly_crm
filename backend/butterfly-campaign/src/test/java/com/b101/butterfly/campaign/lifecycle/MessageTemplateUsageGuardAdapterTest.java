package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.lifecycle.application.MessageTemplateUsageGuardAdapter;
import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
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
    @DisplayName("연결된 캠페인이 없으면 통과한다")
    void noCampaignsAllow() {
        given(campaignRepository.findAllByMessageTemplateId(TEMPLATE_ID))
                .willReturn(List.of());

        assertThatCode(() -> guard.assertNotInUseByActiveCampaign(TEMPLATE_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACTIVE가 아닌 캠페인은 통과시킨다 (validateTemplateUpdatable이 던지지 않음)")
    void nonActiveCampaignsAllow() {
        Campaign passive = mock(Campaign.class);
        given(campaignRepository.findAllByMessageTemplateId(TEMPLATE_ID))
                .willReturn(List.of(passive, mock(Campaign.class)));

        assertThatCode(() -> guard.assertNotInUseByActiveCampaign(TEMPLATE_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACTIVE 캠페인이 하나라도 있으면 CAMPAIGN_IS_ACTIVE 예외를 전파한다")
    void activeCampaignThrows() {
        Campaign active = mock(Campaign.class);
        doThrow(new BusinessException(CampaignErrorCode.CAMPAIGN_IS_ACTIVE))
                .when(active).validateTemplateUpdatable();
        given(campaignRepository.findAllByMessageTemplateId(TEMPLATE_ID))
                .willReturn(List.of(active));

        assertThatThrownBy(() -> guard.assertNotInUseByActiveCampaign(TEMPLATE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.CAMPAIGN_IS_ACTIVE);
    }
}
