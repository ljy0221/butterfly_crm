package com.b101.butterfly.campaign.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.campaign.domain.Campaign;
import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.campaign.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Campaign 도메인 메서드")
class CampaignDomainTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    private MessageTemplate template() {
        return MessageTemplate.create(
                PROJECT_ID, MEMBER_ID,
                "T", "T-desc", Channel.PUSH, "T-title", "T-body", null);
    }

    private Campaign draftCampaign() {
        return Campaign.immediateAll(
                PROJECT_ID, template(), MEMBER_ID, "캠페인-A", "설명-A");
    }

    @Nested
    @DisplayName("isOwnedBy")
    class Ownership {
        @Test
        @DisplayName("같은 projectId면 true")
        void sameProjectIsOwned() {
            assertThat(draftCampaign().isOwnedBy(PROJECT_ID)).isTrue();
        }

        @Test
        @DisplayName("다른 projectId면 false")
        void otherProjectIsNotOwned() {
            assertThat(draftCampaign().isOwnedBy(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("update — DRAFT 일반 필드 수정")
    class Update {
        @Test
        @DisplayName("DRAFT 상태에서 이름/설명을 수정한다")
        void updateInDraft() {
            Campaign c = draftCampaign();
            c.update("새이름", "새설명");
            assertThat(c.getCampaignName()).isEqualTo("새이름");
            assertThat(c.getCampaignDescription()).isEqualTo("새설명");
        }

        @Test
        @DisplayName("DRAFT가 아닌 상태에서는 NOT_MODIFIABLE 예외")
        void updateInNonDraftRejected() {
            Campaign c = draftCampaign();
            c.activate();  // → ACTIVE
            assertThatThrownBy(() -> c.update("x", "y"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.NOT_MODIFIABLE);
        }

        @Test
        @DisplayName("이름이 비어 있으면 CAMPAIGN_NAME_REQUIRED 예외")
        void blankNameRejected() {
            Campaign c = draftCampaign();
            assertThatThrownBy(() -> c.update("  ", "설명"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_NAME_REQUIRED);
        }
    }

    @Nested
    @DisplayName("상태 전이 — activate/pause/resume/complete")
    class Transitions {
        @Test
        @DisplayName("activate: DRAFT → ACTIVE")
        void activateFromDraft() {
            Campaign c = draftCampaign();
            c.activate();
            assertThat(c.getCampaignStatus()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("activate: 이미 ACTIVE인 경우 INVALID_STATUS_TRANSITION")
        void activateInActiveRejected() {
            Campaign c = draftCampaign();
            c.activate();
            assertThatThrownBy(c::activate)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("pause: ACTIVE → PAUSED")
        void pauseFromActive() {
            Campaign c = draftCampaign();
            c.activate();
            c.pause();
            assertThat(c.getCampaignStatus()).isEqualTo(CampaignStatus.PAUSED);
        }

        @Test
        @DisplayName("pause: DRAFT에서는 INVALID_STATUS_TRANSITION")
        void pauseFromDraftRejected() {
            Campaign c = draftCampaign();
            assertThatThrownBy(c::pause)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("resume: PAUSED → ACTIVE")
        void resumeFromPaused() {
            Campaign c = draftCampaign();
            c.activate();
            c.pause();
            c.resume();
            assertThat(c.getCampaignStatus()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("complete: ACTIVE → ENDED")
        void completeFromActive() {
            Campaign c = draftCampaign();
            c.activate();
            c.complete();
            assertThat(c.getCampaignStatus()).isEqualTo(CampaignStatus.ENDED);
        }

        @Test
        @DisplayName("complete: PAUSED → ENDED")
        void completeFromPaused() {
            Campaign c = draftCampaign();
            c.activate();
            c.pause();
            c.complete();
            assertThat(c.getCampaignStatus()).isEqualTo(CampaignStatus.ENDED);
        }

        @Test
        @DisplayName("complete: DRAFT에서는 INVALID_STATUS_TRANSITION")
        void completeFromDraftRejected() {
            Campaign c = draftCampaign();
            assertThatThrownBy(c::complete)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("ENDED는 종착 상태 — 어떤 전이도 거부")
        void endedIsTerminal() {
            Campaign c = draftCampaign();
            c.activate();
            c.complete();
            assertThatThrownBy(c::activate)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_STATUS_TRANSITION);
            assertThatThrownBy(c::pause)
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(c::resume)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("transitionTo — 일반화된 진입점 (PATCH .../status가 사용)")
    class GenericTransition {
        @Test
        @DisplayName("DRAFT → ACTIVE 허용")
        void draftToActive() {
            Campaign c = draftCampaign();
            assertThatCode(() -> c.transitionTo(CampaignStatus.ACTIVE))
                    .doesNotThrowAnyException();
            assertThat(c.getCampaignStatus()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("DRAFT → ENDED 비허용")
        void draftToEndedRejected() {
            Campaign c = draftCampaign();
            assertThatThrownBy(() -> c.transitionTo(CampaignStatus.ENDED))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
