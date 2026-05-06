package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.ScheduleSpec;
import com.b101.butterfly.campaign.lifecycle.domain.Targeting;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignType;
import com.b101.butterfly.campaign.lifecycle.domain.enums.RepeatType;
import com.b101.butterfly.campaign.lifecycle.domain.enums.SendMode;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Campaign.create — Sealed VO 단일 진입점")
class CampaignSealedVoTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long TEMPLATE_ID = 100L;

    private static MessageTemplateSnapshot template() {
        return new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH);
    }

    private static Campaign.CampaignCommonInputs commonInputs() {
        return new Campaign.CampaignCommonInputs(
                PROJECT_ID, template(), MEMBER_ID, "이름", "설명");
    }

    @Nested
    @DisplayName("ScheduleSpec variants")
    class ScheduleVariants {

        @Test
        @DisplayName("Immediate + All → ONE_TIME / IMMEDIATE / segmentId null")
        void immediateAll() {
            Campaign c = Campaign.create(
                    commonInputs(), new ScheduleSpec.Immediate(), new Targeting.All());
            assertThat(c.getCampaignType()).isEqualTo(CampaignType.ONE_TIME);
            assertThat(c.getSendMode()).isEqualTo(SendMode.IMMEDIATE);
            assertThat(c.getSegmentId()).isNull();
            assertThat(c.getChannel()).isEqualTo(Channel.PUSH);
            assertThat(c.getMessageTemplateId()).isEqualTo(TEMPLATE_ID);
        }

        @Test
        @DisplayName("Immediate + Segment(200) → segmentId 200")
        void immediateSegment() {
            Campaign c = Campaign.create(
                    commonInputs(), new ScheduleSpec.Immediate(), new Targeting.Segment(200L));
            assertThat(c.getSegmentId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("Scheduled(미래) + All → SCHEDULED / nextRunAt 셋팅 / recurringCount 1")
        void scheduledFuture() {
            LocalDateTime future = LocalDateTime.now().plusDays(1);
            Campaign c = Campaign.create(
                    commonInputs(),
                    new ScheduleSpec.Scheduled(future),
                    new Targeting.All());
            assertThat(c.getSendMode()).isEqualTo(SendMode.SCHEDULED);
            assertThat(c.getNextRunAt()).isEqualTo(future);
            assertThat(c.getRecurringCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Scheduled 과거 시각 → SCHEDULED_AT_IN_PAST")
        void scheduledPastRejected() {
            LocalDateTime past = LocalDateTime.now().minusDays(1);
            assertThatThrownBy(() -> Campaign.create(
                    commonInputs(),
                    new ScheduleSpec.Scheduled(past),
                    new Targeting.All()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.SCHEDULED_AT_IN_PAST);
        }

        @Test
        @DisplayName("Recurring DAILY → cron 생성 + RECURRING")
        void recurringDaily() {
            LocalDateTime next = LocalDateTime.now().plusDays(1);
            Campaign c = Campaign.create(
                    commonInputs(),
                    new ScheduleSpec.Recurring(
                            RepeatType.DAILY, null, null,
                            LocalTime.of(9, 0), 5, next),
                    new Targeting.All());
            assertThat(c.getSendMode()).isEqualTo(SendMode.RECURRING);
            assertThat(c.getRecurringCron()).isEqualTo("0 0 9 * * *");
            assertThat(c.getRecurringCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Automation → CampaignType=AUTOMATION / SendMode null")
        void automation() {
            Campaign c = Campaign.create(
                    commonInputs(), new ScheduleSpec.Automation(), new Targeting.All());
            assertThat(c.getCampaignType()).isEqualTo(CampaignType.AUTOMATION);
            assertThat(c.getSendMode()).isNull();
        }
    }

    @Nested
    @DisplayName("Targeting variants")
    class TargetingVariants {

        @Test
        @DisplayName("Targeting.Segment(null) → CAMPAIGN_SEGMENT_REQUIRED")
        void segmentNullRejected() {
            assertThatThrownBy(() -> Campaign.create(
                    commonInputs(),
                    new ScheduleSpec.Immediate(),
                    new Targeting.Segment(null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_SEGMENT_REQUIRED);
        }
    }

    @Nested
    @DisplayName("validateCommon")
    class CommonValidation {

        @Test
        @DisplayName("template null → CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED")
        void nullTemplateRejected() {
            Campaign.CampaignCommonInputs bad = new Campaign.CampaignCommonInputs(
                    PROJECT_ID, null, MEMBER_ID, "n", "d");
            assertThatThrownBy(() -> Campaign.create(
                    bad, new ScheduleSpec.Immediate(), new Targeting.All()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
        }

        @Test
        @DisplayName("memberId null → CAMPAIGN_MEMBER_REQUIRED")
        void nullMemberRejected() {
            Campaign.CampaignCommonInputs bad = new Campaign.CampaignCommonInputs(
                    PROJECT_ID, template(), null, "n", "d");
            assertThatThrownBy(() -> Campaign.create(
                    bad, new ScheduleSpec.Immediate(), new Targeting.All()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_MEMBER_REQUIRED);
        }
    }
}
