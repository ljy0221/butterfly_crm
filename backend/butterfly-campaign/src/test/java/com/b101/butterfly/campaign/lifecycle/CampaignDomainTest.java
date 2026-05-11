package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.ScheduleSpec;
import com.b101.butterfly.campaign.lifecycle.domain.Targeting;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Campaign 도메인 메서드")
class CampaignDomainTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long TEMPLATE_ID = 100L;

    private MessageTemplateSnapshot template() {
        return new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH);
    }

    private Campaign draftCampaign() {
        Campaign.CampaignCommonInputs common = new Campaign.CampaignCommonInputs(
                PROJECT_ID, template(), MEMBER_ID, "캠페인-A", "설명-A");
        return Campaign.create(common, new ScheduleSpec.Immediate(), new Targeting.All());
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
    @DisplayName("update — DRAFT 일반 필드 + template/segment 수정")
    class Update {
        @Test
        @DisplayName("DRAFT 상태에서 이름/설명을 수정한다 (template/segment 동일)")
        void updateInDraft() {
            Campaign c = draftCampaign();
            c.update("새이름", "새설명", TEMPLATE_ID, Channel.PUSH, null);
            assertThat(c.getCampaignName()).isEqualTo("새이름");
            assertThat(c.getCampaignDescription()).isEqualTo("새설명");
            assertThat(c.getMessageTemplateId()).isEqualTo(TEMPLATE_ID);
            assertThat(c.getChannel()).isEqualTo(Channel.PUSH);
            assertThat(c.getSegmentId()).isNull();
        }

        @Test
        @DisplayName("messageTemplateId 변경 시 channel 도 함께 갱신된다 (호출자 책임)")
        void updateChangesTemplateAndChannel() {
            Campaign c = draftCampaign();
            Long newTemplateId = 200L;
            c.update("이름", "설명", newTemplateId, Channel.EMAIL, null);
            assertThat(c.getMessageTemplateId()).isEqualTo(newTemplateId);
            assertThat(c.getChannel()).isEqualTo(Channel.EMAIL);
        }

        @Test
        @DisplayName("segmentId 를 null → 값 으로 변경 (Targeting.All → Targeting.Segment)")
        void updateSetsSegmentId() {
            Campaign c = draftCampaign();
            c.update("이름", "설명", TEMPLATE_ID, Channel.PUSH, 42L);
            assertThat(c.getSegmentId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("segmentId 를 값 → null 로 변경 (Targeting.Segment → Targeting.All)")
        void updateClearsSegmentId() {
            Campaign.CampaignCommonInputs common = new Campaign.CampaignCommonInputs(
                    PROJECT_ID, template(), MEMBER_ID, "이름", "설명");
            Campaign c = Campaign.create(common, new ScheduleSpec.Immediate(), new Targeting.Segment(42L));
            c.update("이름", "설명", TEMPLATE_ID, Channel.PUSH, null);
            assertThat(c.getSegmentId()).isNull();
        }

        @Test
        @DisplayName("DRAFT가 아닌 상태에서는 NOT_MODIFIABLE 예외")
        void updateInNonDraftRejected() {
            Campaign c = draftCampaign();
            c.activate();  // → ACTIVE
            assertThatThrownBy(() -> c.update("x", "y", TEMPLATE_ID, Channel.PUSH, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.NOT_MODIFIABLE);
        }

        @Test
        @DisplayName("이름이 비어 있으면 CAMPAIGN_NAME_REQUIRED 예외")
        void blankNameRejected() {
            Campaign c = draftCampaign();
            assertThatThrownBy(() -> c.update("  ", "설명", TEMPLATE_ID, Channel.PUSH, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_NAME_REQUIRED);
        }

        @Test
        @DisplayName("messageTemplateId 가 null 이면 CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED 예외")
        void nullTemplateIdRejected() {
            Campaign c = draftCampaign();
            assertThatThrownBy(() -> c.update("이름", "설명", null, Channel.PUSH, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
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
